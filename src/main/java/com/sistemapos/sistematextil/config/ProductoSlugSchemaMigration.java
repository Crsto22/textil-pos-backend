package com.sistemapos.sistematextil.config;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.HashSet;
import java.util.Set;

import javax.sql.DataSource;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import com.sistemapos.sistematextil.services.ProductoService;

@Component
public class ProductoSlugSchemaMigration implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(ProductoSlugSchemaMigration.class);
    private static final String TABLE_NAME = "producto";
    private static final String COLUMN_NAME = "slug";
    private static final String INDEX_NAME = "uk_producto_slug";

    private final DataSource dataSource;

    public ProductoSlugSchemaMigration(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void run(ApplicationArguments args) throws Exception {
        try (Connection connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            if (!tableExists(connection, TABLE_NAME)) {
                return;
            }
            asegurarColumna(connection, statement);
            rellenarSlugs(connection);
            asegurarIndice(connection, statement);
        }
    }

    private void asegurarColumna(Connection connection, Statement statement) throws Exception {
        if (columnExists(connection, TABLE_NAME, COLUMN_NAME)) {
            return;
        }
        statement.execute("""
                ALTER TABLE producto
                ADD COLUMN slug VARCHAR(180) DEFAULT NULL AFTER nombre
                """);
        log.info("Columna {}.{} creada", TABLE_NAME, COLUMN_NAME);
    }

    private void rellenarSlugs(Connection connection) throws Exception {
        Set<String> usados = new HashSet<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT slug
                FROM producto
                WHERE slug IS NOT NULL
                  AND TRIM(slug) <> ''
                """);
                ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                usados.add(resultSet.getString("slug"));
            }
        }

        try (PreparedStatement select = connection.prepareStatement("""
                SELECT producto_id, nombre
                FROM producto
                WHERE slug IS NULL
                   OR TRIM(slug) = ''
                ORDER BY producto_id ASC
                """);
                PreparedStatement update = connection.prepareStatement("""
                UPDATE producto
                SET slug = ?
                WHERE producto_id = ?
                """);
                ResultSet resultSet = select.executeQuery()) {
            while (resultSet.next()) {
                Integer idProducto = resultSet.getInt("producto_id");
                String nombre = resultSet.getString("nombre");
                String slug = generarSlugUnico(nombre, usados);
                update.setString(1, slug);
                update.setInt(2, idProducto);
                update.addBatch();
                usados.add(slug);
            }
            update.executeBatch();
        }
    }

    private String generarSlugUnico(String nombre, Set<String> usados) {
        String base = ProductoService.normalizarSlug(nombre);
        if (base == null) {
            base = "producto";
        }
        if (base.length() > 180) {
            base = base.substring(0, 180).replaceAll("-+$", "");
        }
        String candidato = base;
        int sufijo = 2;
        while (usados.contains(candidato)) {
            String suffix = "-" + sufijo++;
            int maxBaseLength = Math.max(1, 180 - suffix.length());
            String baseCortada = base.length() > maxBaseLength
                    ? base.substring(0, maxBaseLength).replaceAll("-+$", "")
                    : base;
            candidato = baseCortada + suffix;
        }
        return candidato;
    }

    private void asegurarIndice(Connection connection, Statement statement) throws Exception {
        if (indexExists(connection, TABLE_NAME, INDEX_NAME)) {
            return;
        }
        statement.execute("ALTER TABLE producto ADD UNIQUE KEY uk_producto_slug (slug)");
        log.info("Indice {} creado en {}", INDEX_NAME, TABLE_NAME);
    }

    private boolean tableExists(Connection connection, String tableName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.TABLES
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                """)) {
            statement.setString(1, tableName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean columnExists(Connection connection, String tableName, String columnName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.COLUMNS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND COLUMN_NAME = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, columnName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }

    private boolean indexExists(Connection connection, String tableName, String indexName) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT COUNT(*)
                FROM information_schema.STATISTICS
                WHERE TABLE_SCHEMA = DATABASE()
                  AND TABLE_NAME = ?
                  AND INDEX_NAME = ?
                """)) {
            statement.setString(1, tableName);
            statement.setString(2, indexName);
            try (ResultSet resultSet = statement.executeQuery()) {
                return resultSet.next() && resultSet.getInt(1) > 0;
            }
        }
    }
}
