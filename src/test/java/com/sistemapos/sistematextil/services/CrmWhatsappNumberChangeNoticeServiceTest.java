package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.CrmWhatsappArea;
import com.sistemapos.sistematextil.model.CrmWhatsappConnection;
import com.sistemapos.sistematextil.model.CrmWhatsappConversation;
import com.sistemapos.sistematextil.model.CrmWhatsappMessage;
import com.sistemapos.sistematextil.repositories.CrmWhatsappConversationRepository;
import com.sistemapos.sistematextil.repositories.CrmWhatsappMessageRepository;

class CrmWhatsappNumberChangeNoticeServiceTest {

    @Test
    void registersOnlyOneNoticeForEachPhoneNumberVersion() {
        CrmWhatsappConversationRepository conversations = mock(CrmWhatsappConversationRepository.class);
        CrmWhatsappMessageRepository messages = mock(CrmWhatsappMessageRepository.class);
        CrmWhatsappNumberChangeNoticeService service =
                new CrmWhatsappNumberChangeNoticeService(conversations, messages);

        CrmWhatsappConnection connection = new CrmWhatsappConnection();
        connection.setArea(CrmWhatsappArea.SHIPPING);
        connection.setConnectedNumber("51987654321:1@s.whatsapp.net");
        connection.setPhoneNumberVersion(1);

        CrmWhatsappConversation conversation = new CrmWhatsappConversation();
        conversation.setIdConversation(10L);
        conversation.setConnection(connection);
        conversation.setPhoneNumberVersion(0);

        when(conversations.findForUpdateById(10L)).thenReturn(Optional.of(conversation));
        when(messages.save(any(CrmWhatsappMessage.class))).thenAnswer(invocation -> invocation.getArgument(0));

        CrmWhatsappMessage notice = service.registerIfNeeded(10L, 55).orElseThrow();
        assertEquals("WAREHOUSE_SYSTEM", notice.getOrigin());
        assertEquals(55, notice.getRelatedSaleId());
        assertTrue(notice.getBody().contains("Envíos"));
        assertTrue(notice.getBody().contains("+51 987 654 321"));
        assertEquals(1, conversation.getPhoneNumberVersion());

        assertTrue(service.registerIfNeeded(10L, 55).isEmpty());
        verify(messages, times(1)).save(any(CrmWhatsappMessage.class));
    }
}
