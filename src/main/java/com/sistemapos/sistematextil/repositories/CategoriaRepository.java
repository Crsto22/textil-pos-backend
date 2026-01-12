package com.sistemapos.sistematextil.repositories;

import org.springframework.data.jpa.repository.JpaRepository;

import com.sistemapos.sistematextil.model.Categoria;

public interface CategoriaRepository extends JpaRepository<Categoria, Integer> {
}