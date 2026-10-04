package com.sistemapos.sistematextil.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.repositories.ClienteRepository;

class CrmWhatsappAiClientWriterTest {

    private final ClienteRepository repository = mock(ClienteRepository.class);
    private final CrmWhatsappAiClientWriter writer = new CrmWhatsappAiClientWriter(repository);

    @Test
    void encuentraClienteAunqueEsteEliminadoLogicamente() {
        Cliente client = new Cliente();
        client.setIdCliente(8);
        when(repository.findByEmpresa_IdEmpresaAndTelefonoInOrderByIdClienteAsc(
                1, List.of("932889985", "51932889985"))).thenReturn(List.of(client));

        var result = writer.findMatchesIncludingDeleted(1, List.of("932889985", "51932889985"));

        assertEquals(8, result.getFirst().getIdCliente());
    }

    @Test
    void restauraClienteSinCambiarSuNombre() {
        Cliente client = new Cliente();
        client.setIdCliente(8);
        client.setNombres("Leonardo");
        client.setEstado("INACTIVO");
        client.setDeletedAt(LocalDateTime.now());
        when(repository.findById(8)).thenReturn(Optional.of(client));
        when(repository.saveAndFlush(client)).thenReturn(client);

        Cliente restored = writer.restore(8);

        assertEquals("Leonardo", restored.getNombres());
        assertEquals("ACTIVO", restored.getEstado());
        assertNull(restored.getDeletedAt());
        verify(repository).saveAndFlush(client);
    }
}
