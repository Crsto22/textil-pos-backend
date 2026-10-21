package com.sistemapos.sistematextil.services;

import java.util.Optional;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.CrmWhatsappArea;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappNumberChangeNoticeService {
    private final CrmWhatsappConversationRepository conversationRepository;
    private final CrmWhatsappMessageRepository messageRepository;

    @Transactional
    public Optional<CrmWhatsappMessage> registerIfNeeded(Long conversationId, Integer relatedSaleId) {
        CrmWhatsappConversation conversation = conversationRepository.findForUpdateById(conversationId).orElse(null);
        if (conversation == null || conversation.getConnection() == null) return Optional.empty();

        CrmWhatsappConnection connection = conversation.getConnection();
        int connectionVersion = value(connection.getPhoneNumberVersion());
        int conversationVersion = value(conversation.getPhoneNumberVersion());
        if (connectionVersion <= conversationVersion) return Optional.empty();

        CrmWhatsappArea area = connection.getArea() == null ? CrmWhatsappArea.SALES : connection.getArea();
        CrmWhatsappMessage notice = new CrmWhatsappMessage();
        notice.setConversation(conversation);
        notice.setDirection("SYSTEM");
        notice.setOrigin(area == CrmWhatsappArea.SHIPPING ? "WAREHOUSE_SYSTEM" : "CRM_SYSTEM");
        notice.setMessageType("TEXT");
        notice.setBody("La conversación continuará desde el nuevo número de "
                + (area == CrmWhatsappArea.SHIPPING ? "Envíos" : "Ventas") + ": "
                + displayNumber(connection.getConnectedNumber()) + ".");
        notice.setMessageStatus("sent");
        notice.setRelatedSaleId(relatedSaleId);
        notice = messageRepository.save(notice);

        conversation.setPhoneNumberVersion(connectionVersion);
        conversationRepository.save(conversation);
        return Optional.of(notice);
    }

    @Transactional
    public List<CrmWhatsappMessage> registerForConnection(Long connectionId) {
        if (connectionId == null) return List.of();
        return conversationRepository.findByConnection_IdConnection(connectionId).stream()
                .map(conversation -> registerIfNeeded(conversation.getIdConversation(), null).orElse(null))
                .filter(java.util.Objects::nonNull)
                .toList();
    }

    private int value(Integer value) {
        return value == null ? 0 : value;
    }

    private String displayNumber(String value) {
        String raw = value == null ? "" : value;
        int at = raw.indexOf('@');
        if (at >= 0) raw = raw.substring(0, at);
        int device = raw.indexOf(':');
        if (device >= 0) raw = raw.substring(0, device);
        String digits = raw.replaceAll("\\D", "");
        if (digits.length() == 11 && digits.startsWith("51")) {
            return "+51 " + digits.substring(2, 5) + " " + digits.substring(5, 8) + " " + digits.substring(8);
        }
        return digits.isBlank() ? "nuevo número" : "+" + digits;
    }
}
