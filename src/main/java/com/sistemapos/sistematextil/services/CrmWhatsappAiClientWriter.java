package com.sistemapos.sistematextil.services;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.sistemapos.sistematextil.model.Cliente;
import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.model.Usuario;
import com.sistemapos.sistematextil.repositories.ClienteRepository;
import com.sistemapos.sistematextil.util.cliente.TipoDocumento;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CrmWhatsappAiClientWriter {
    private final ClienteRepository clienteRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cliente create(Empresa empresa, Usuario actor, String name, String phone) {
        return create(empresa, actor, TipoDocumento.SIN_DOC, null, name, phone, null, null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cliente create(
            Empresa empresa,
            Usuario actor,
            TipoDocumento documentType,
            String documentNumber,
            String name,
            String phone,
            String email,
            String address) {
        Cliente client = new Cliente();
        client.setEmpresa(empresa);
        client.setUsuarioCreacion(actor);
        client.setTipoDocumento(documentType == null ? TipoDocumento.SIN_DOC : documentType);
        client.setNroDocumento(documentNumber);
        client.setNombres(name);
        client.setTelefono(phone);
        client.setCorreo(email);
        client.setDireccion(address);
        client.setEstado("ACTIVO");
        return clienteRepository.saveAndFlush(client);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Cliente> findMatches(Integer companyId, List<String> phones) {
        return clienteRepository.findByEmpresa_IdEmpresaAndTelefonoInAndDeletedAtIsNullOrderByIdClienteAsc(
                companyId, phones);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Cliente> findMatchesIncludingDeleted(Integer companyId, List<String> phones) {
        return clienteRepository.findByEmpresa_IdEmpresaAndTelefonoInOrderByIdClienteAsc(companyId, phones);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public Cliente restore(Integer clientId) {
        Cliente client = clienteRepository.findById(clientId)
                .orElseThrow(() -> new IllegalStateException("Cliente no encontrado"));
        client.setDeletedAt(null);
        client.setEstado("ACTIVO");
        return clienteRepository.saveAndFlush(client);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public List<Cliente> findMatchesAfterConflict(Integer companyId, List<String> phones) {
        List<Cliente> matches = List.of();
        for (int attempt = 0; attempt < 3; attempt++) {
            matches = clienteRepository.findByEmpresa_IdEmpresaAndTelefonoInOrderByIdClienteAsc(companyId, phones);
            if (!matches.isEmpty()) return matches;
            try {
                Thread.sleep(25L * (attempt + 1));
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                return matches;
            }
        }
        return matches;
    }
}
