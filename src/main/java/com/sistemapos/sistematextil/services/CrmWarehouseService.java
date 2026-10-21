package com.sistemapos.sistematextil.services;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.CrmWarehouseOrder;
import com.sistemapos.sistematextil.model.CrmWarehouseStatus;
import com.sistemapos.sistematextil.model.CrmWhatsappArea;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappFulfillment;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.model.Producto;
import com.sistemapos.sistematextil.model.ProductoVariante;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.model.Venta;
import com.sistemapos.sistematextil.model.VentaDetalle;
import com.sistemapos.sistematextil.repositories.CrmWarehouseOrderRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConnectionRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;
import com.sistemapos.sistematextil.repositories.ProductoColorImagenRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.VentaDetalleRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.util.crm.CrmWhatsappPhoneUtils;
import com.sistemapos.sistematextil.util.producto.ProductoImagenColorRow;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWarehouseService {
    private final CrmWarehouseOrderRepository orderRepository;
    private final CrmWhatsappConnectionRepository connectionRepository;
    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappMessageRepository messageRepository;
    private final CrmWhatsappFulfillmentRepository fulfillmentRepository;
    private final VentaDetalleRepository saleDetailRepository;
    private final VentaRepository saleRepository;
    private final ProductoColorImagenRepository productImageRepository;
    private final SucursalRepository sucursalRepository;
    private final CrmWhatsappBridgeService bridgeService;
    private final CrmWhatsappConnectionStateService connectionStateService;
    private final CrmWhatsappNumberChangeNoticeService numberChangeNoticeService;
    private final CrmWarehouseEventService warehouseEventService;
    private final S3StorageService storageService;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Transactional
    public WarehousePage list(Usuario actor, String query, String status, String sortOrder, int page, int size) {
        requireWarehouse(actor);
        CrmWarehouseStatus parsedStatus = parseStatus(status, true);
        String term = clean(query);
        boolean ascending = "FIRST".equalsIgnoreCase(clean(sortOrder));
        PageRequest pageable = PageRequest.of(Math.max(0, page), Math.min(100, Math.max(1, size)));
        Integer branchId = branchScopeId(actor);
        var result = term.isBlank()
                ? orderRepository.searchWithoutTerm(companyId(actor), branchId, parsedStatus, ascending, pageable)
                : orderRepository.searchWithTerm(companyId(actor),
                        branchId, parsedStatus == null ? null : parsedStatus.name(), term, ascending, pageable);
        result.getContent().forEach(order -> {
            resolveSalesConversation(order);
            resolveExistingShippingConversation(order, actor);
        });
        return new WarehousePage(result.getContent().stream().map(this::summary).toList(),
                result.getTotalElements(), result.getNumber(), result.getSize(), result.getTotalPages());
    }

    @Transactional
    public WarehouseDetail get(Integer saleId, Usuario actor) {
        CrmWarehouseOrder order = requireOrder(saleId, actor);
        resolveSalesConversation(order);
        resolveExistingShippingConversation(order, actor);
        return detail(order);
    }

    @Transactional
    public ShippingConversationReference openShippingConversation(Integer saleId, Usuario actor) {
        requireWarehouse(actor);
        CrmWarehouseOrder order = orderRepository.findByVenta_IdVenta(saleId).orElseGet(() -> {
            Venta sale = saleRepository.findById(saleId)
                    .filter(item -> item.getDeletedAt() == null)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada en Envios"));
            if (!companyId(actor).equals(companyId(sale)))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada en Envios");
            Integer branchId = branchScopeId(actor);
            if (branchId != null && !branchId.equals(sale.getSucursal().getIdSucursal()))
                throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada en Envios");
            CrmWarehouseOrder created = new CrmWarehouseOrder();
            created.setVenta(sale);
            return orderRepository.save(created);
        });
        if (!companyId(actor).equals(companyId(order.getVenta())))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada en Envios");
        Integer branchId = branchScopeId(actor);
        if (branchId != null && !branchId.equals(order.getVenta().getSucursal().getIdSucursal()))
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada en Envios");
        CrmWhatsappConversation conversation = requireShippingConversation(order, actor);
        return new ShippingConversationReference(conversation.getIdConversation());
    }

    @Transactional
    public WarehouseDetail updateStatus(Integer saleId, StatusRequest request, Usuario actor) {
        CrmWarehouseOrder order = requireOrder(saleId, actor);
        if (isCancelled(order.getVenta())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Una venta anulada no admite cambios de Envios");
        }
        CrmWarehouseStatus status = parseStatus(request == null ? null : request.status(), false);
        LocalDateTime now = LocalDateTime.now();
        order.setStatus(status);
        order.setUpdatedBy(actor);
        if (status == CrmWarehouseStatus.IN_PROGRESS && order.getStartedAt() == null) order.setStartedAt(now);
        if (status == CrmWarehouseStatus.COMPLETED) order.setCompletedAt(now);
        if (status != CrmWarehouseStatus.COMPLETED) order.setCompletedAt(null);
        WarehouseDetail result = detail(orderRepository.save(order));
        publish("warehouse.status.updated", saleId);
        return result;
    }

    @Transactional
    public MessageDto sendMessage(Integer saleId, MessageRequest request, Usuario actor) {
        CrmWarehouseOrder order = requireOrder(saleId, actor);
        requireActionable(order);
        String body = clean(request == null ? null : request.message());
        if (body.isBlank()) throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "message es requerido");
        CrmWhatsappConversation conversation = requireShippingConversation(order, actor);
        numberChangeNoticeService.registerIfNeeded(conversation.getIdConversation(), saleId);
        boolean firstContact = !messageRepository.existsByConversation_IdConversationAndRelatedSaleIdAndOrigin(
                conversation.getIdConversation(), saleId, "WAREHOUSE");
        ResponseEntity<String> response = bridgeService.enviarTexto(CrmWhatsappArea.SHIPPING,
                conversation.getPhone(), body, null, UUID.randomUUID().toString());
        requireBridgeSuccess(response);
        beginWarehouseAttention(order, conversation, actor, firstContact);
        CrmWhatsappMessage message = outgoingMessage(conversation, body, "TEXT", response.getBody());
        message.setRelatedSaleId(saleId);
        message = messageRepository.save(message);
        touchConversation(conversation, body, "TEXT", message.getCreatedAt());
        publish("warehouse.message.updated", saleId);
        return message(message);
    }

    @Transactional
    public MessageDto sendMedia(Integer saleId, MultipartFile file, String caption, Usuario actor) {
        CrmWarehouseOrder order = requireOrder(saleId, actor);
        requireActionable(order);
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "file es requerido");
        }
        CrmWhatsappConversation conversation = requireShippingConversation(order, actor);
        numberChangeNoticeService.registerIfNeeded(conversation.getIdConversation(), saleId);
        boolean firstContact = !messageRepository.existsByConversation_IdConversationAndRelatedSaleIdAndOrigin(
                conversation.getIdConversation(), saleId, "WAREHOUSE");
        String fileName = safeFileName(file.getOriginalFilename());
        String mimeType = clean(file.getContentType());
        if (mimeType.isBlank()) mimeType = "application/octet-stream";
        YearMonth month = YearMonth.now();
        String storageKey = "crm/whatsapp/warehouse/%04d/%02d/%s-%s".formatted(
                month.getYear(), month.getMonthValue(), UUID.randomUUID(), fileName);
        String storagePath = null;
        try {
            byte[] bytes = file.getBytes();
            storagePath = storageService.upload(bytes, storageKey, mimeType);
            ResponseEntity<String> response = bridgeService.enviarMedia(CrmWhatsappArea.SHIPPING,
                    conversation.getPhone(), bytes, fileName, mimeType, clean(caption), null,
                    UUID.randomUUID().toString());
            requireBridgeSuccess(response);
            beginWarehouseAttention(order, conversation, actor, firstContact);
            String messageType = mediaType(mimeType);
            String body = clean(caption).isBlank() ? fileName : clean(caption);
            CrmWhatsappMessage message = outgoingMessage(conversation, body, messageType, response.getBody());
            message.setRelatedSaleId(saleId);
            message.setMediaFileName(fileName);
            message.setMediaMimeType(mimeType);
            message.setMediaStoragePath(storagePath);
            message = messageRepository.save(message);
            touchConversation(conversation, body, messageType, message.getCreatedAt());
            publish("warehouse.message.updated", saleId);
            return message(message);
        } catch (ResponseStatusException exception) {
            if (storagePath != null) storageService.deleteByKey(storagePath);
            throw exception;
        } catch (Exception exception) {
            if (storagePath != null) storageService.deleteByKey(storagePath);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar el archivo por WhatsApp");
        }
    }

    @Transactional
    public MessagePage salesMessages(Integer saleId, Long beforeId, Long afterId, int limit, Usuario actor) {
        CrmWarehouseOrder order = requireOrder(saleId, actor);
        resolveSalesConversation(order);
        return messages(order.getSalesConversation(), beforeId, afterId, limit);
    }

    @Transactional
    public MessagePage shippingMessages(Integer saleId, Long beforeId, Long afterId, int limit, Usuario actor) {
        CrmWarehouseOrder order = requireOrder(saleId, actor);
        resolveExistingShippingConversation(order, actor);
        return messages(order.getShippingConversation(), beforeId, afterId, limit);
    }

    @Transactional(readOnly = true)
    public MediaDownload downloadMedia(Long messageId, Usuario actor) {
        requireWarehouse(actor);
        CrmWhatsappMessage message = messageRepository.findById(messageId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Mensaje no encontrado"));
        if (!orderRepository.canAccessConversation(
                companyId(actor), branchScopeId(actor), message.getConversation().getIdConversation())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tiene acceso a este archivo");
        }
        if (message.getDeletedAt() != null || clean(message.getMediaStoragePath()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "El mensaje no tiene archivo disponible");
        }
        return new MediaDownload(storageService.readBytes(message.getMediaStoragePath()),
                clean(message.getMediaMimeType()).isBlank() ? "application/octet-stream" : message.getMediaMimeType(),
                safeFileName(message.getMediaFileName()));
    }

    private CrmWarehouseOrder requireOrder(Integer saleId, Usuario actor) {
        requireWarehouse(actor);
        CrmWarehouseOrder order = orderRepository.findByVenta_IdVenta(saleId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada en Envios"));
        if (!companyId(actor).equals(companyId(order.getVenta()))) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada en Envios");
        }
        Integer branchId = branchScopeId(actor);
        if (branchId != null && !branchId.equals(order.getVenta().getSucursal().getIdSucursal())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada en Envios");
        }
        if (isCancelled(order.getVenta())) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Venta no encontrada en Envios");
        }
        return order;
    }

    private void resolveSalesConversation(CrmWarehouseOrder order) {
        if (order.getSalesConversation() != null) return;
        CrmWhatsappConversation conversation = fulfillmentRepository.findByVenta_IdVenta(order.getVenta().getIdVenta())
                .map(CrmWhatsappFulfillment::getConversation)
                .orElseGet(() -> messageRepository
                        .findFirstByRelatedSaleIdOrderByIdMessageDesc(order.getVenta().getIdVenta())
                        .map(CrmWhatsappMessage::getConversation).orElse(null));
        if (conversation != null && conversation.getConnection() != null
                && conversation.getConnection().getArea() == CrmWhatsappArea.SALES) {
            order.setSalesConversation(conversation);
            orderRepository.save(order);
        }
    }

    private void resolveExistingShippingConversation(CrmWarehouseOrder order, Usuario actor) {
        if (order.getShippingConversation() != null) return;
        String phone = contactPhone(order);
        if (phone.isBlank()) return;
        CrmWhatsappConnection connection = shippingConnection(actor);
        conversationRepository.findByConnection_IdConnectionAndPhone(connection.getIdConnection(), chatId(phone))
                .ifPresent(conversation -> {
                    order.setShippingConversation(conversation);
                    orderRepository.save(order);
                });
    }

    private CrmWhatsappConversation requireShippingConversation(CrmWarehouseOrder order, Usuario actor) {
        resolveExistingShippingConversation(order, actor);
        if (order.getShippingConversation() != null) return order.getShippingConversation();
        String phone = contactPhone(order);
        if (phone.isBlank()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Sin telefono disponible para contactar al cliente");
        }
        CrmWhatsappConnection connection = shippingConnection(actor);
        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setConnection(connection);
        conversation.setPhone(chatId(phone));
        conversation.setPhoneNumber(phone);
        Cliente client = order.getVenta().getCliente();
        conversation.setCliente(client);
        conversation.setContactName(client == null ? null : client.getNombres());
        conversation.setStatus("ATENDIDO");
        conversation.setUnreadCount(0);
        conversation.setPhoneNumberVersion(connection.getPhoneNumberVersion() == null
                ? 0 : connection.getPhoneNumberVersion());
        conversation = conversationRepository.save(conversation);
        order.setShippingConversation(conversation);
        orderRepository.save(order);
        return conversation;
    }

    private CrmWhatsappConnection shippingConnection(Usuario actor) {
        return connectionRepository.findByEmpresa_IdEmpresaAndArea(companyId(actor), CrmWhatsappArea.SHIPPING)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT,
                        "Configure el WhatsApp de Envios en Conexiones"));
    }

    private WarehouseSummary summary(CrmWarehouseOrder order) {
        Venta sale = order.getVenta();
        Cliente client = sale.getCliente();
        return new WarehouseSummary(sale.getIdVenta(), receiptNumber(sale), sale.getTipoComprobante(),
                sale.getFecha(), sale.getTotal(), sale.getMoneda(), sale.getEstado(),
                client == null ? "Cliente sin registrar" : client.getNombres(), contactPhone(order),
                sale.getSucursal() == null ? "" : sale.getSucursal().getNombre(), order.getStatus(),
                order.getSalesConversation() != null, order.getShippingConversation() != null,
                isCancelled(sale));
    }

    private WarehouseDetail detail(CrmWarehouseOrder order) {
        Venta sale = order.getVenta();
        Cliente client = sale.getCliente();
        List<VentaDetalle> saleItems = saleDetailRepository
                .findByVenta_IdVentaAndDeletedAtIsNullOrderByIdVentaDetalleAsc(sale.getIdVenta());
        List<Integer> productIds = saleItems.stream()
                .map(VentaDetalle::getProductoVariante)
                .filter(java.util.Objects::nonNull)
                .map(ProductoVariante::getProducto)
                .filter(java.util.Objects::nonNull)
                .map(Producto::getIdProducto)
                .filter(java.util.Objects::nonNull)
                .distinct()
                .toList();
        List<ProductoImagenColorRow> colorImages = productIds.isEmpty()
                ? List.of()
                : productImageRepository.obtenerResumenPorProductos(productIds);
        List<ItemDto> items = saleItems.stream().map(item -> item(item, colorImages)).toList();
        CrmWhatsappConnection shipping = connectionRepository
                .findByEmpresa_IdEmpresaAndArea(companyId(sale), CrmWhatsappArea.SHIPPING).orElse(null);
        boolean operational = shipping != null && connectionStateService.isOperational(shipping);
        return new WarehouseDetail(summary(order), client == null ? null : client.getNroDocumento(),
                client == null ? null : client.getDireccion(), items,
                order.getSalesConversation() == null ? null : order.getSalesConversation().getIdConversation(),
                order.getShippingConversation() == null ? null : order.getShippingConversation().getIdConversation(),
                operational, operational ? null : "WhatsApp de Envios desconectado",
                order.getStartedAt(), order.getCompletedAt(), order.getUpdatedAt());
    }

    private ItemDto item(VentaDetalle detail, List<ProductoImagenColorRow> colorImages) {
        ProductoVariante variant = detail.getProductoVariante();
        Producto product = variant == null ? null : variant.getProducto();
        Integer productId = product == null ? null : product.getIdProducto();
        Integer colorId = variant == null || variant.getColor() == null ? null : variant.getColor().getIdColor();
        String imageUrl = colorImages.stream()
                .filter(image -> java.util.Objects.equals(image.productoId(), productId)
                        && java.util.Objects.equals(image.colorId(), colorId))
                .map(image -> firstNotBlank(image.urlThumb(), image.url()))
                .filter(url -> !url.isBlank())
                .findFirst()
                .orElseGet(() -> product == null ? ""
                        : firstNotBlank(product.getImagenGlobalThumbUrl(), product.getImagenGlobalUrl()));
        String name = clean(detail.getDescripcion());
        if (name.isBlank() && product != null) name = clean(product.getNombre());
        return new ItemDto(name, detail.getCantidad(), detail.getPrecioUnitario(),
                detail.getDescuento(), detail.getTotalDetalle(), imageUrl);
    }

    private String firstNotBlank(String preferred, String fallback) {
        String value = clean(preferred);
        return value.isBlank() ? clean(fallback) : value;
    }

    private MessagePage messages(CrmWhatsappConversation conversation, Long beforeId, Long afterId, int requestedLimit) {
        if (beforeId != null && afterId != null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "beforeId y afterId no pueden combinarse");
        }
        if (conversation == null) return new MessagePage(List.of(), null, null, false);
        int limit = Math.min(50, Math.max(1, requestedLimit));
        List<CrmWhatsappMessage> selected;
        boolean hasMoreBefore;
        if (afterId != null) {
            selected = messageRepository.findMessagesAfter(conversation.getIdConversation(), afterId,
                    PageRequest.of(0, limit));
            hasMoreBefore = false;
        } else {
            selected = messageRepository.findMessageHistory(conversation.getIdConversation(), beforeId,
                    PageRequest.of(0, limit + 1));
            hasMoreBefore = selected.size() > limit;
            if (hasMoreBefore) selected = selected.subList(0, limit);
            selected = new java.util.ArrayList<>(selected);
            java.util.Collections.reverse(selected);
        }
        List<MessageDto> content = selected.stream().map(this::message).toList();
        Long oldestId = content.isEmpty() ? null : content.getFirst().id();
        Long newestId = content.isEmpty() ? null : content.getLast().id();
        return new MessagePage(content, oldestId, newestId, hasMoreBefore);
    }

    private MessageDto message(CrmWhatsappMessage message) {
        boolean deleted = message.getDeletedAt() != null;
        return new MessageDto(message.getIdMessage(), message.getDirection(), message.getMessageType(),
                deleted ? "Mensaje eliminado" : message.getBody(), message.getMessageStatus(), message.getCreatedAt(),
                deleted ? null : message.getMediaMimeType(), deleted ? null : message.getMediaFileName(),
                deleted || clean(message.getMediaStoragePath()).isBlank() ? null
                        : "/api/crm/warehouse/messages/" + message.getIdMessage() + "/media",
                deleted ? null : message.getMediaDurationSeconds(), message.getOrigin(),
                message.getRelatedSaleId(), message.getReceiptFormat(), deleted);
    }

    private CrmWhatsappMessage outgoingMessage(CrmWhatsappConversation conversation, String body,
            String type, String json) {
        CrmWhatsappMessage message = new CrmWhatsappMessage();
        message.setConversation(conversation);
        message.setDirection("OUTGOING");
        message.setOrigin("WAREHOUSE");
        message.setMessageType(type);
        message.setBody(body);
        message.setMessageStatus("sent");
        try {
            JsonNode root = objectMapper.readTree(json == null ? "{}" : json);
            if (root.path("messageId").isTextual()) message.setWhatsappMessageId(root.path("messageId").asText());
            if (!root.path("messageKey").isMissingNode()) message.setMessageKeyJson(root.path("messageKey").toString());
            if (!root.path("baileysMessage").isMissingNode()) message.setBaileysMessageJson(root.path("baileysMessage").toString());
        } catch (Exception ignored) {
        }
        return message;
    }

    private void beginWarehouseAttention(CrmWarehouseOrder order, CrmWhatsappConversation conversation,
            Usuario actor, boolean firstContact) {
        LocalDateTime now = LocalDateTime.now();
        if (order.getStatus() == CrmWarehouseStatus.PENDING) {
            order.setStatus(CrmWarehouseStatus.IN_PROGRESS);
            order.setUpdatedBy(actor);
            if (order.getStartedAt() == null) order.setStartedAt(now);
            orderRepository.save(order);
        }
        if (!firstContact) return;
        CrmWhatsappMessage event = new CrmWhatsappMessage();
        event.setConversation(conversation);
        event.setDirection("SYSTEM");
        event.setOrigin("WAREHOUSE_SYSTEM");
        event.setMessageType("SYSTEM");
        event.setBody(fullName(actor) + " inici\u00f3 la atenci\u00f3n de Env\u00edos.");
        event.setMessageStatus("sent");
        event.setRelatedSaleId(order.getVenta().getIdVenta());
        messageRepository.save(event);
    }

    private String fullName(Usuario actor) {
        String name = (clean(actor == null ? null : actor.getNombre()) + " "
                + clean(actor == null ? null : actor.getApellido())).trim();
        return name.isBlank() ? "Usuario de Env\u00edos" : name;
    }

    private void touchConversation(CrmWhatsappConversation conversation, String body, String type, LocalDateTime at) {
        conversation.setStatus("ATENDIDO");
        conversation.setUnreadCount(0);
        conversation.setLastMessage(body);
        conversation.setLastMessageType(type);
        conversation.setLastMessageAt(at);
        conversationRepository.save(conversation);
    }

    private void requireBridgeSuccess(ResponseEntity<String> response) {
        if (response == null || !response.getStatusCode().is2xxSuccessful()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, "No se pudo enviar el mensaje por WhatsApp");
        }
    }

    private void publish(String type, Integer saleId) {
        warehouseEventService.publishAfterCommit(java.util.Map.of("type", type, "saleId", saleId));
    }

    private void requireActionable(CrmWarehouseOrder order) {
        if (isCancelled(order.getVenta())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "La venta esta anulada");
        }
    }

    private void requireWarehouse(Usuario actor) {
        if (actor == null || actor.getRol() == null || !actor.getRol().permiteAlmacen()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Acceso exclusivo para Envios");
        }
    }

    private Integer companyId(Usuario actor) {
        if (actor != null && actor.getSucursal() != null && actor.getSucursal().getEmpresa() != null) {
            return actor.getSucursal().getEmpresa().getIdEmpresa();
        }
        if (actor != null && actor.getRol() != null && actor.getRol().esAdministrador()) {
            return sucursalRepository.findByDeletedAtIsNullAndEstadoOrderByIdSucursalAsc("ACTIVO").stream()
                    .filter(sucursal -> sucursal.getEmpresa() != null)
                    .findFirst()
                    .map(sucursal -> sucursal.getEmpresa().getIdEmpresa())
                    .orElseThrow(() -> new ResponseStatusException(
                            HttpStatus.FORBIDDEN,
                            "No hay una empresa activa asignable"));
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario no tiene empresa asignada");
    }

    private Integer companyId(Venta sale) {
        return sale.getSucursal().getEmpresa().getIdEmpresa();
    }

    private Integer branchScopeId(Usuario actor) {
        if (actor != null && actor.getRol() != null && actor.getRol().esAdministrador()) {
            return null;
        }
        if (actor != null && actor.getSucursal() != null && actor.getSucursal().getIdSucursal() != null) {
            return actor.getSucursal().getIdSucursal();
        }
        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "El usuario no tiene sucursal asignada");
    }

    private String contactPhone(CrmWarehouseOrder order) {
        Venta sale = order.getVenta();
        if (sale.getCliente() != null) {
            String normalized = CrmWhatsappPhoneUtils.normalizePeruvianMobile(sale.getCliente().getTelefono());
            if (!normalized.isBlank()) return normalized;
        }
        String fulfillmentPhone = fulfillmentRepository.findByVenta_IdVenta(sale.getIdVenta())
                .map(CrmWhatsappFulfillment::getPhone).orElse("");
        String normalized = CrmWhatsappPhoneUtils.normalizePeruvianMobile(fulfillmentPhone);
        if (!normalized.isBlank()) return normalized;
        resolveSalesConversation(order);
        if (order.getSalesConversation() == null) return "";
        normalized = CrmWhatsappPhoneUtils.normalizePeruvianMobile(order.getSalesConversation().getPhoneNumber());
        return normalized.isBlank()
                ? CrmWhatsappPhoneUtils.normalizePeruvianMobile(order.getSalesConversation().getPhone())
                : normalized;
    }

    private String chatId(String phone) {
        String normalized = CrmWhatsappPhoneUtils.normalizePeruvianMobile(phone);
        return normalized.isBlank() ? "" : "51" + normalized + "@s.whatsapp.net";
    }

    private CrmWarehouseStatus parseStatus(String value, boolean allAllowed) {
        String normalized = clean(value).toUpperCase(Locale.ROOT);
        if (allAllowed && (normalized.isBlank() || "ALL".equals(normalized))) return null;
        try {
            return CrmWarehouseStatus.valueOf(normalized);
        } catch (Exception exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Estado de Envios invalido");
        }
    }

    private boolean isCancelled(Venta sale) {
        return "ANULADA".equalsIgnoreCase(clean(sale.getEstado()));
    }

    private String receiptNumber(Venta sale) {
        String serie = clean(sale.getSerie());
        return serie.isBlank() ? String.valueOf(sale.getCorrelativo() == null ? "" : sale.getCorrelativo())
                : serie + "-" + String.format("%06d", sale.getCorrelativo() == null ? 0 : sale.getCorrelativo());
    }

    private String mediaType(String mime) {
        if (mime.startsWith("image/")) return "IMAGE";
        if (mime.startsWith("video/")) return "VIDEO";
        if (mime.startsWith("audio/")) return "AUDIO";
        return "DOCUMENT";
    }

    private String safeFileName(String value) {
        String name = clean(value).replaceAll("[\\\\/:*?\"<>|]", "_");
        return name.isBlank() ? "archivo" : name;
    }

    private String clean(Object value) {
        return value == null ? "" : value.toString().trim();
    }

    public record WarehousePage(List<WarehouseSummary> content, long totalElements, int page, int size,
            int totalPages) {}
    public record WarehouseSummary(Integer saleId, String number, String receiptType, LocalDateTime createdAt,
            BigDecimal total, String currency, String saleStatus, String customerName, String customerPhone,
            String branch, CrmWarehouseStatus warehouseStatus, boolean hasSalesChat, boolean hasShippingChat,
            boolean cancelled) {}
    public record WarehouseDetail(WarehouseSummary sale, String customerDocument, String customerAddress,
            List<ItemDto> items, Long salesConversationId, Long shippingConversationId,
            boolean shippingOperational, String shippingBlockedReason, LocalDateTime startedAt,
            LocalDateTime completedAt, LocalDateTime updatedAt) {}
    public record ItemDto(String description, Integer quantity, BigDecimal unitPrice, BigDecimal discount,
            BigDecimal total, String imageUrl) {}
    public record MessageDto(Long id, String direction, String messageType, String body, String status,
            LocalDateTime createdAt, String mediaMimeType, String mediaFileName, String mediaUrl,
            Integer mediaDurationSeconds, String origin, Integer relatedSaleId, String receiptFormat,
            boolean deleted) {}
    public record MessagePage(List<MessageDto> content, Long oldestId, Long newestId,
            boolean hasMoreBefore) {}
    public record MessageRequest(String message) {}
    public record StatusRequest(String status) {}
    public record ShippingConversationReference(Long conversationId) {}
    public record MediaDownload(byte[] bytes, String contentType, String fileName) {}
}
