package com.sistemapos.sistematextil.services;

import java.time.LocalDateTime;
import java.util.List;

import org.springframework.stereotype.Service;

import com.sistemapos.sistematextil.model.Empresa;
import com.sistemapos.sistematextil.repositories.EmpresaRepository;

import lombok.AllArgsConstructor;

@Service
@AllArgsConstructor
public class EmpresaService {

    private final EmpresaRepository empresaRepository;

    public List <Empresa> listarTodas(){
        return empresaRepository.findAll();
    }

    public Empresa insertar (Empresa empresa){
        if (empresa.getFechaCreacion() == null) {
            empresa.setFechaCreacion(LocalDateTime.now());
        }
        return empresaRepository.save(empresa);
    }

    public Empresa obtenerPorId(Integer idEmpresa) {
        return empresaRepository.findById(idEmpresa).orElseThrow(() -> new RuntimeException("La empresa con ID " + idEmpresa + " no existe"));
    }


    public Empresa actualizar (Integer idEmpresa , Empresa empresa){
        if (!empresaRepository.existsById(idEmpresa)) {
            throw new RuntimeException("La empresa no existe");
        }
        
        Empresa original = empresaRepository.findById(idEmpresa).get();
        empresa.setIdEmpresa(idEmpresa);
        empresa.setFechaCreacion(original.getFechaCreacion());
        return empresaRepository.save(empresa);
    }

    public void eliminar(Integer idEmpresa){
        if (!empresaRepository.existsById(idEmpresa)) {
            throw new RuntimeException("La empresa " +idEmpresa+" no existe");
        }
        empresaRepository.deleteById(idEmpresa);
    }

}
