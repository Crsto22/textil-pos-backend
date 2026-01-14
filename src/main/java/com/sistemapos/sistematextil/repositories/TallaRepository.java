package com.sistemapos.sistematextil.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.Talla;

@Repository
public interface TallaRepository extends JpaRepository<Talla, Integer> {
}