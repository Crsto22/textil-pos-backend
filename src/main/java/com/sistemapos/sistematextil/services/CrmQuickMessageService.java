package com.sistemapos.sistematextil.services;

import java.io.ByteArrayInputStream;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import javax.imageio.ImageIO;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import com.sistemapos.sistematextil.model.CrmQuickMessage;
import com.sistemapos.sistematextil.model.CrmQuickMessageBlock;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.CrmQuickMessageBlockRepository;
import com.sistemapos.sistematextil.repositories.CrmQuickMessageRepository;
import com.sistemapos.sistematextil.repositories.SucursalRepository;
import com.sistemapos.sistematextil.repositories.UsuarioRepository;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.MessageResponse;
import com.sistemapos.sistematextil.services.CrmWhatsappChatService.SendMessageRequest;
import com.sistemapos.sistematextil.util.usuario.Rol;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmQuickMessageService {

    private static final int MAX_IMAGE_BYTES = 10 * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 4000;

    private final CrmQuickMessageRepository commandRepository;
    private final CrmQuickMessageBlockRepository blockRepository;
    private final UsuarioRepository usuarioRepository;
    private final SucursalRepository sucursalRepository;
    private final S3StorageService storageService;
    private final CrmWhatsappChatService chatService;

    @Transactional(readOnly = true)
    public List<QuickMessageResponse> list(String query, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        String normalizedQuery = clean(query).toLowerCase(Locale.ROOT);
        return commandRepository.findByEmpresa_IdEmpresaAndDeletedAtIsNullOrderByKeywordAsc(companyId(actor))
                .stream()
                .filter(command -> normalizedQuery.isBlank()
                        || command.getKeyword().toLowerCase(Locale.ROOT).contains(normalizedQuery))
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public QuickMessageResponse get(Long commandId, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        return toResponse(requireCommand(commandId, companyId(actor)));
    }

    @Transactional
    public QuickMessageResponse create(QuickMessageRequest request, List<MultipartFile> files, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        Integer companyId = companyId(actor);
        String keyword = normalizeKeyword(request == null ? null : request.keyword());
        validateUniqueKeyword(companyId, keyword, null);

        CrmQuickMessage command = new CrmQuickMessage();
        command.setEmpresa(company(actor));
        command.setCreatedBy(actor);
        command.setKeyword(keyword);
        command = commandRepository.save(command);
        replaceBlocks(command, request == null ? null : request.blocks(), files);
        return toResponse(commandRepository.saveAndFlush(command));
    }

    @Transactional
    public QuickMessageResponse update(Long commandId, QuickMessageRequest request, List<MultipartFile> files,
            Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        Integer companyId = companyId(actor);
        CrmQuickMessage command = requireCommand(commandId, companyId);
        String keyword = normalizeKeyword(request == null ? null : request.keyword());
        validateUniqueKeyword(companyId, keyword, commandId);
        command.setKeyword(keyword);
        replaceBlocks(command, request == null ? null : request.blocks(), files);
        return toResponse(commandRepository.saveAndFlush(command));
    }

    @Transactional
    public void delete(Long commandId, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmQuickMessage command = requireCommand(commandId, companyId(actor));
        List<String> mediaToDelete = activeBlocksWithMedia(command).stream()
                .map(CrmQuickMessageBlock::getMediaStoragePath).toList();
        LocalDateTime now = LocalDateTime.now();
        command.setDeletedAt(now);
        for (CrmQuickMessageBlock block : activeBlocks(command)) {
            block.setDeletedAt(now);
        }
        commandRepository.saveAndFlush(command);
        mediaToDelete.forEach(this::safeDelete);
    }

    @Transactional(readOnly = true)
    public QuickMessageMedia download(Long commandId, Long blockId, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        CrmQuickMessageBlock block = blockRepository
                .findByIdBlockAndQuickMessage_IdQuickMessageAndQuickMessage_Empresa_IdEmpresaAndDeletedAtIsNull(
                        blockId, commandId, companyId(actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada"));
        if (!"IMAGE".equals(block.getBlockType()) || clean(block.getMediaStoragePath()).isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Imagen no encontrada");
        }
        return new QuickMessageMedia(storageService.readBytes(block.getMediaStoragePath()),
                block.getMediaMimeType(), block.getMediaFileName());
    }

    public SendQuickMessageResponse send(Long commandId, SendQuickMessageRequest request, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        if (request == null || request.conversationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId es requerido");
        }
        QuickMessageSnapshot command = snapshot(commandId, companyId(actor));
        List<MessageResponse> sent = new ArrayList<>();
        String error = null;
        Integer failedPosition = null;
        for (QuickMessageBlockSnapshot block : command.blocks()) {
            try {
                sent.add(sendSnapshotBlock(block, request.conversationId(), actor));
            } catch (RuntimeException exception) {
                error = exception instanceof ResponseStatusException statusException
                        ? statusException.getReason()
                        : exception.getMessage();
                if (clean(error).isBlank()) error = "No se pudo enviar el bloque";
                failedPosition = block.position();
                break;
            }
        }
        String status = error == null ? "COMPLETED" : sent.isEmpty() ? "FAILED" : "PARTIAL";
        return new SendQuickMessageResponse(status, command.blocks().size(), sent.size(), failedPosition, error, sent);
    }

    public MessageResponse sendBlock(Long commandId, Long blockId, SendQuickMessageRequest request, Usuario sessionUser) {
        Usuario actor = requireCrmUser(sessionUser);
        if (request == null || request.conversationId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "conversationId es requerido");
        }
        CrmQuickMessageBlock block = blockRepository
                .findByIdBlockAndQuickMessage_IdQuickMessageAndQuickMessage_Empresa_IdEmpresaAndDeletedAtIsNull(
                        blockId, commandId, companyId(actor))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Bloque no encontrado"));
        return sendSnapshotBlock(new QuickMessageBlockSnapshot(block.getPosition(), block.getBlockType(), block.getBody(),
                block.getMediaStoragePath(), block.getMediaFileName(), block.getMediaMimeType()),
                request.conversationId(), actor);
    }

    private MessageResponse sendSnapshotBlock(QuickMessageBlockSnapshot block, Long conversationId, Usuario actor) {
        return "TEXT".equals(block.type())
                ? chatService.enviarMensaje(conversationId, new SendMessageRequest(block.body(), null), actor)
                : chatService.enviarMediaDesdeStorage(conversationId, block.storagePath(), block.fileName(),
                        block.mimeType(), block.body(), null, actor);
    }

    @Transactional(readOnly = true)
    protected QuickMessageSnapshot snapshot(Long commandId, Integer companyId) {
        CrmQuickMessage command = requireCommand(commandId, companyId);
        List<QuickMessageBlockSnapshot> blocks = activeBlocks(command).stream()
                .map(block -> new QuickMessageBlockSnapshot(block.getPosition(), block.getBlockType(), block.getBody(),
                        block.getMediaStoragePath(), block.getMediaFileName(), block.getMediaMimeType()))
                .toList();
        return new QuickMessageSnapshot(command.getIdQuickMessage(), blocks);
    }

    private void replaceBlocks(CrmQuickMessage command, List<QuickMessageBlockRequest> requests,
            List<MultipartFile> uploadedFiles) {
        if (requests == null || requests.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Agrega al menos un bloque");
        }
        List<MultipartFile> files = uploadedFiles == null ? List.of() : uploadedFiles;
        long newImageCount = requests.stream()
                .filter(block -> block != null && "IMAGE".equals(normalizeType(block.type())) && block.id() == null)
                .count();
        if (newImageCount != files.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Las imagenes del comando estan incompletas");
        }

        Map<Long, CrmQuickMessageBlock> existing = new HashMap<>();
        for (CrmQuickMessageBlock block : activeBlocks(command)) existing.put(block.getIdBlock(), block);
        Set<Long> retainedIds = new HashSet<>();
        List<String> uploadedPaths = new ArrayList<>();
        int fileIndex = 0;
        try {
            for (int index = 0; index < requests.size(); index++) {
                QuickMessageBlockRequest request = requests.get(index);
                if (request == null) throw badRequest("Bloque invalido");
                String type = normalizeType(request.type());
                String body = clean(request.body());
                if (body.length() > MAX_TEXT_LENGTH) throw badRequest("El texto no puede superar 4000 caracteres");
                if ("TEXT".equals(type) && body.isBlank()) throw badRequest("El bloque de texto no puede estar vacio");

                CrmQuickMessageBlock block;
                if (request.id() != null) {
                    block = existing.get(request.id());
                    if (block == null || !block.getBlockType().equals(type)) throw badRequest("Bloque no valido");
                    retainedIds.add(block.getIdBlock());
                    block.setDeletedAt(null);
                } else {
                    block = new CrmQuickMessageBlock();
                    block.setQuickMessage(command);
                    block.setBlockType(type);
                    command.getBlocks().add(block);
                    if ("IMAGE".equals(type)) {
                        MultipartFile file = files.get(fileIndex++);
                        validateImage(file);
                        String fileName = safeFileName(file.getOriginalFilename());
                        String mimeType = detectImageMime(readBytes(file));
                        YearMonth month = YearMonth.now();
                        String path = storageService.upload(readBytes(file),
                                "crm/quick-messages/%d/%04d/%02d/%s-%s".formatted(
                                        command.getEmpresa().getIdEmpresa(), month.getYear(), month.getMonthValue(),
                                        UUID.randomUUID(), fileName), mimeType);
                        uploadedPaths.add(path);
                        block.setMediaStoragePath(path);
                        block.setMediaFileName(fileName);
                        block.setMediaMimeType(mimeType);
                    }
                }
                block.setPosition(index);
                block.setBody(body);
            }

            LocalDateTime now = LocalDateTime.now();
            for (CrmQuickMessageBlock block : existing.values()) {
                if (!retainedIds.contains(block.getIdBlock())) block.setDeletedAt(now);
            }
            blockRepository.saveAll(command.getBlocks());
            blockRepository.flush();
            for (CrmQuickMessageBlock block : existing.values()) {
                if (!retainedIds.contains(block.getIdBlock()) && !clean(block.getMediaStoragePath()).isBlank()) {
                    safeDelete(block.getMediaStoragePath());
                }
            }
        } catch (RuntimeException exception) {
            uploadedPaths.forEach(this::safeDelete);
            throw exception;
        }
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) throw badRequest("Selecciona una imagen");
        if (file.getSize() > MAX_IMAGE_BYTES) throw badRequest("Cada imagen debe pesar hasta 10 MB");
        if (detectImageMime(readBytes(file)) == null) throw badRequest("El archivo no es una imagen valida");
    }

    private String detectImageMime(byte[] bytes) {
        if (bytes == null || bytes.length < 4) return null;
        if (bytes[0] == (byte) 0xFF && bytes[1] == (byte) 0xD8) return "image/jpeg";
        if (bytes[0] == (byte) 0x89 && bytes[1] == 0x50 && bytes[2] == 0x4E && bytes[3] == 0x47) return "image/png";
        if (bytes[0] == 0x47 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x38) return "image/gif";
        if (bytes.length >= 12 && bytes[0] == 0x52 && bytes[1] == 0x49 && bytes[2] == 0x46 && bytes[3] == 0x46
                && bytes[8] == 0x57 && bytes[9] == 0x45 && bytes[10] == 0x42 && bytes[11] == 0x50) return "image/webp";
        try {
            return ImageIO.read(new ByteArrayInputStream(bytes)) == null ? null : "image/jpeg";
        } catch (Exception ignored) {
            return null;
        }
    }

    private byte[] readBytes(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw badRequest("No se pudo leer la imagen");
        }
    }

    private void validateUniqueKeyword(Integer companyId, String keyword, Long currentId) {
        commandRepository.findFirstByEmpresa_IdEmpresaAndKeywordIgnoreCaseAndDeletedAtIsNull(companyId, keyword)
                .filter(existing -> currentId == null || !existing.getIdQuickMessage().equals(currentId))
                .ifPresent(existing -> { throw new ResponseStatusException(HttpStatus.CONFLICT, "El comando ya existe"); });
    }

    private String normalizeKeyword(String value) {
        String keyword = clean(value).toLowerCase(Locale.ROOT);
        if (keyword.startsWith("/")) keyword = keyword.substring(1);
        if (!keyword.matches("[a-z0-9][a-z0-9_-]{0,39}")) {
            throw badRequest("Usa hasta 40 letras, numeros, guion o guion bajo");
        }
        return keyword;
    }

    private String normalizeType(String value) {
        String type = clean(value).toUpperCase(Locale.ROOT);
        if (!Set.of("TEXT", "IMAGE").contains(type)) throw badRequest("Tipo de bloque no valido");
        return type;
    }

    private CrmQuickMessage requireCommand(Long id, Integer companyId) {
        return commandRepository.findByIdQuickMessageAndEmpresa_IdEmpresaAndDeletedAtIsNull(id, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Comando no encontrado"));
    }

    private Usuario requireCrmUser(Usuario sessionUser) {
        if (sessionUser == null || sessionUser.getIdUsuario() == null) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        Usuario actor = usuarioRepository.findByIdUsuarioAndDeletedAtIsNull(sessionUser.getIdUsuario())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
        if (!"ACTIVO".equalsIgnoreCase(clean(actor.getEstado()))) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        if (actor.getRol() != Rol.ADMINISTRADOR && !Boolean.TRUE.equals(actor.getAccesoCrm())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "No tienes acceso al CRM");
        }
        return actor;
    }

    private Empresa company(Usuario actor) {
        if (actor.getSucursal() != null && actor.getSucursal().getEmpresa() != null) return actor.getSucursal().getEmpresa();
        return sucursalRepository.findByDeletedAtIsNullAndEstadoOrderByIdSucursalAsc("ACTIVO").stream()
                .findFirst().filter(branch -> branch.getEmpresa() != null).map(branch -> branch.getEmpresa())
                .orElseThrow(() -> badRequest("No hay empresa registrada"));
    }

    private Integer companyId(Usuario actor) {
        return company(actor).getIdEmpresa();
    }

    private List<CrmQuickMessageBlock> activeBlocks(CrmQuickMessage command) {
        return command.getBlocks().stream().filter(block -> block.getDeletedAt() == null)
                .sorted((left, right) -> Integer.compare(left.getPosition(), right.getPosition())).toList();
    }

    private List<CrmQuickMessageBlock> activeBlocksWithMedia(CrmQuickMessage command) {
        return activeBlocks(command).stream().filter(block -> !clean(block.getMediaStoragePath()).isBlank()).toList();
    }

    private QuickMessageResponse toResponse(CrmQuickMessage command) {
        String creator = command.getCreatedBy() == null ? "Usuario"
                : (clean(command.getCreatedBy().getNombre()) + " " + clean(command.getCreatedBy().getApellido())).trim();
        List<QuickMessageBlockResponse> blocks = activeBlocks(command).stream()
                .map(block -> new QuickMessageBlockResponse(block.getIdBlock(), block.getBlockType(), block.getPosition(),
                        block.getBody(), block.getMediaFileName(), block.getMediaMimeType(),
                        "IMAGE".equals(block.getBlockType())
                                ? "/api/crm/whatsapp/quick-messages/%d/blocks/%d/media".formatted(
                                        command.getIdQuickMessage(), block.getIdBlock())
                                : null))
                .toList();
        return new QuickMessageResponse(command.getIdQuickMessage(), command.getKeyword(), blocks, creator,
                command.getCreatedAt(), command.getUpdatedAt());
    }

    private String safeFileName(String value) {
        String name = clean(value).replace("\\", "/");
        int slash = name.lastIndexOf('/');
        if (slash >= 0) name = name.substring(slash + 1);
        name = name.replaceAll("[\\p{Cntrl}<>:\"|?*]+", "_").trim();
        return name.isBlank() ? "imagen" : name;
    }

    private void safeDelete(String path) {
        try { storageService.deleteByKey(path); } catch (RuntimeException ignored) { }
    }

    private String clean(String value) { return value == null ? "" : value.trim(); }
    private ResponseStatusException badRequest(String message) { return new ResponseStatusException(HttpStatus.BAD_REQUEST, message); }

    public record QuickMessageRequest(String keyword, List<QuickMessageBlockRequest> blocks) { }
    public record QuickMessageBlockRequest(Long id, String type, String body) { }
    public record QuickMessageResponse(Long id, String keyword, List<QuickMessageBlockResponse> blocks,
            String createdBy, LocalDateTime createdAt, LocalDateTime updatedAt) { }
    public record QuickMessageBlockResponse(Long id, String type, Integer position, String body,
            String fileName, String mimeType, String mediaUrl) { }
    public record SendQuickMessageRequest(Long conversationId) { }
    public record SendQuickMessageResponse(String status, int total, int sentCount, Integer failedPosition,
            String error, List<MessageResponse> messages) { }
    public record QuickMessageMedia(byte[] bytes, String contentType, String fileName) { }
    private record QuickMessageSnapshot(Long id, List<QuickMessageBlockSnapshot> blocks) { }
    private record QuickMessageBlockSnapshot(Integer position, String type, String body, String storagePath,
            String fileName, String mimeType) { }
}
