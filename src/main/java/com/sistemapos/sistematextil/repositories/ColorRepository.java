package com.sistemapos.sistematextil.repositories;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.sistemapos.sistematextil.model.Color;

@Repository
public interface ColorRepository extends JpaRepository<Color, Integer> {
}