package com.sistemapos.sistematextil.services.agent.film;

import java.time.LocalDateTime;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappFilmAddon;
import com.sistemapos.sistematextil.model.CrmWhatsappFilmStatus;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFilmAddonRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappFulfillmentRepository;
import com.sistemapos.sistematextil.repositories.VentaRepository;
import com.sistemapos.sistematextil.services.agent.outbox.AgentOutboxService;
import com.sistemapos.sistematextil.services.agent.sale.AgentSaleService;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class AgentFilmFinalizationService {
    private final CrmWhatsappFilmAddonRepository addons;
    private final CrmWhatsappFulfillmentRepository fulfillments;
    private final VentaRepository sales;
    private final AgentSaleService saleService;
    private final AgentOutboxService outbox;

    @Transactional
    public FinalResult finalizeSale(Long addonId, boolean notifyCustomer, String noticePrefix) {
        CrmWhatsappFilmAddon addon = addons.findForUpdate(addonId).orElseThrow();
        var sale = saleService.createSalesNote(addon.getAgentSession().getIdAgentSession(),
                "film-final:" + addonId);
        if (addon.getVenta() == null) {
            addon.setVenta(sales.findById(sale.saleId()).orElseThrow());
            addons.save(addon);
        }
        if (notifyCustomer && addon.getFinalMessageSentAt() == null) {
            String text = finalMessage(addon, sale.saleId(), noticePrefix);
            outbox.enqueueOperationalText(addon.getAgentSession().getIdAgentSession(), null, null,
                    92_000, text);
            addon.setFinalMessageSentAt(LocalDateTime.now()); addons.save(addon);
        } else if (!notifyCustomer && addon.getFinalMessageSentAt() == null) {
            // La respuesta se entregará como salida normal del job conversacional.
            addon.setFinalMessageSentAt(LocalDateTime.now()); addons.save(addon);
        }
        return new FinalResult(sale.saleId(), finalMessage(addon, sale.saleId(), noticePrefix));
    }

    public String finalMessage(CrmWhatsappFilmAddon addon, Integer saleId, String prefix) {
        var f = addon.getFulfillment();
        StringBuilder text = new StringBuilder();
        if (prefix != null && !prefix.isBlank()) text.append(prefix.trim()).append("\n\n");
        if (addon.getStatus() == CrmWhatsappFilmStatus.PAYMENT_OVERDUE
                || addon.getStatus() == CrmWhatsappFilmStatus.PAYMENT_CONFIGURATION_REQUIRED) {
            return text.append("🧾 Número de pedido: ").append(saleId)
                    .append("\n🚌 Tipo de entrega: Envío por Shalom")
                    .append("\n📍 Agencia: ").append(f.getShalomAgency())
                    .append("\n\nUna asesora del área de envíos se comunicará contigo para corroborar tus datos.")
                    .append("\n\n¡Gracias por confiar en Kiments! 💛").toString();
        }
        return text.append("🧾 Número de pedido: ").append(saleId)
                .append("\n🚌 Tipo de entrega: Envío por Shalom")
                .append("\n📍 Agencia: ").append(f.getShalomAgency())
                .append("\n\nUna asesora del área de envíos se comunicará contigo para corroborar tus datos.")
                .append("\n\n¡Gracias por confiar en Kiments! 💛").toString();
    }
    public record FinalResult(Integer saleId, String message) {}
}
