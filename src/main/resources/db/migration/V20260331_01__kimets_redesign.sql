-- KIMETS rediseño: catálogo global, stock por sucursal y canal de venta
-- Ejecutar sobre una copia de la base antes de promover a producción.

-- ============================================================
-- 1. Empresa: datos fiscales centralizados
-- ============================================================
ALTER TABLE empresa
    ADD COLUMN direccion VARCHAR(255) NULL AFTER telefono,
    ADD COLUMN ubigeo VARCHAR(6) NULL AFTER direccion,
    ADD COLUMN departamento VARCHAR(100) NULL AFTER ubigeo,
    ADD COLUMN provincia VARCHAR(100) NULL AFTER departamento,
    ADD COLUMN distrito VARCHAR(100) NULL AFTER provincia,
    ADD COLUMN codigo_establecimiento_sunat VARCHAR(4) NULL DEFAULT '0000' AFTER distrito;

UPDATE empresa e
JOIN sucursal s ON s.id_sucursal = 1
SET e.direccion = s.direccion,
    e.ubigeo = s.ubigeo,
    e.departamento = s.departamento,
    e.provincia = s.provincia,
    e.distrito = s.distrito,
    e.codigo_establecimiento_sunat = COALESCE(s.codigo_establecimiento_sunat, '0000')
WHERE e.id_empresa = 1;

-- ============================================================
-- 2. Sucursal: tipo operativo
-- ============================================================
ALTER TABLE sucursal
    ADD COLUMN tipo ENUM('VENTA', 'ALMACEN') NOT NULL DEFAULT 'VENTA' AFTER descripcion;

UPDATE sucursal SET tipo = 'ALMACEN' WHERE id_sucursal = 1;
UPDATE sucursal SET tipo = 'VENTA' WHERE id_sucursal IN (3, 4);

-- ============================================================
-- 3. Categoría global
-- ============================================================
CREATE TEMPORARY TABLE tmp_categoria_canonica AS
SELECT MIN(id_categoria) AS id_categoria_canonica,
       LOWER(TRIM(nombre_categoria)) AS nombre_categoria_normalizado
FROM categoria
WHERE deleted_at IS NULL
GROUP BY LOWER(TRIM(nombre_categoria));

UPDATE producto p
JOIN categoria c ON c.id_categoria = p.categoria_id
JOIN tmp_categoria_canonica t
  ON t.nombre_categoria_normalizado = LOWER(TRIM(c.nombre_categoria))
SET p.categoria_id = t.id_categoria_canonica;

DELETE c_dup
FROM categoria c_dup
JOIN tmp_categoria_canonica t
  ON t.nombre_categoria_normalizado = LOWER(TRIM(c_dup.nombre_categoria))
WHERE c_dup.id_categoria <> t.id_categoria_canonica;

SET @fk_categoria_sucursal := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'categoria'
      AND COLUMN_NAME = 'id_sucursal'
      AND REFERENCED_TABLE_NAME = 'sucursal'
    LIMIT 1
);
SET @sql := IF(
    @fk_categoria_sucursal IS NOT NULL,
    CONCAT('ALTER TABLE categoria DROP FOREIGN KEY ', @fk_categoria_sucursal),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE categoria
    DROP INDEX uk_categoria_sucursal_nombre,
    DROP COLUMN id_sucursal,
    ADD UNIQUE KEY uk_categoria_nombre (nombre_categoria);

DROP TEMPORARY TABLE IF EXISTS tmp_categoria_canonica;

-- ============================================================
-- 4. Canal de venta
-- ============================================================
CREATE TABLE IF NOT EXISTS canal_venta (
    id_canal_venta INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    id_sucursal INT NOT NULL,
    nombre VARCHAR(100) NOT NULL,
    plataforma ENUM('TIKTOK', 'WHATSAPP', 'INSTAGRAM', 'OTRO') NOT NULL DEFAULT 'OTRO',
    descripcion VARCHAR(255) NULL,
    activo TINYINT(1) NOT NULL DEFAULT 1,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    deleted_at DATETIME(6) NULL,
    UNIQUE KEY uk_canal_venta_sucursal_nombre (id_sucursal, nombre),
    CONSTRAINT fk_canal_venta_sucursal
        FOREIGN KEY (id_sucursal) REFERENCES sucursal(id_sucursal)
);

INSERT IGNORE INTO canal_venta (id_sucursal, nombre, plataforma, descripcion)
VALUES (3, 'TikTok Gamarra', 'TIKTOK', 'En vivo desde Gamarra'),
       (4, 'TikTok San Carlos', 'TIKTOK', 'En vivo desde San Carlos');

-- ============================================================
-- 5. Stock por sucursal
-- ============================================================
CREATE TABLE IF NOT EXISTS sucursal_stock (
    id_sucursal_stock INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    id_sucursal INT NOT NULL,
    id_producto_variante INT NOT NULL,
    cantidad INT NOT NULL DEFAULT 0,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    UNIQUE KEY uq_sucursal_variante (id_sucursal, id_producto_variante),
    CONSTRAINT fk_ss_sucursal
        FOREIGN KEY (id_sucursal) REFERENCES sucursal(id_sucursal),
    CONSTRAINT fk_ss_variante
        FOREIGN KEY (id_producto_variante) REFERENCES producto_variante(id_producto_variante)
);

INSERT INTO sucursal_stock (id_sucursal, id_producto_variante, cantidad)
SELECT sucursal_id, id_producto_variante, COALESCE(stock, 0)
FROM producto_variante
WHERE deleted_at IS NULL;

-- ============================================================
-- 6. Traslado
-- ============================================================
CREATE TABLE IF NOT EXISTS traslado (
    id_traslado INT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    id_sucursal_origen INT NOT NULL,
    id_sucursal_destino INT NOT NULL,
    id_producto_variante INT NOT NULL,
    cantidad INT NOT NULL,
    motivo VARCHAR(255) NULL,
    id_usuario INT NOT NULL,
    fecha DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    CONSTRAINT fk_traslado_origen
        FOREIGN KEY (id_sucursal_origen) REFERENCES sucursal(id_sucursal),
    CONSTRAINT fk_traslado_destino
        FOREIGN KEY (id_sucursal_destino) REFERENCES sucursal(id_sucursal),
    CONSTRAINT fk_traslado_variante
        FOREIGN KEY (id_producto_variante) REFERENCES producto_variante(id_producto_variante),
    CONSTRAINT fk_traslado_usuario
        FOREIGN KEY (id_usuario) REFERENCES usuario(id_usuario)
);

-- ============================================================
-- 7. Venta: canal de venta
-- ============================================================
SET @venta_canal_col := (
    SELECT COUNT(*)
    FROM information_schema.COLUMNS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'venta'
      AND COLUMN_NAME = 'id_canal_venta'
);
SET @sql := IF(
    @venta_canal_col = 0,
    'ALTER TABLE venta ADD COLUMN id_canal_venta INT NULL AFTER id_sucursal',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_venta_canal := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'venta'
      AND COLUMN_NAME = 'id_canal_venta'
      AND REFERENCED_TABLE_NAME = 'canal_venta'
    LIMIT 1
);
SET @sql := IF(
    @fk_venta_canal IS NULL,
    'ALTER TABLE venta ADD CONSTRAINT fk_venta_canal FOREIGN KEY (id_canal_venta) REFERENCES canal_venta(id_canal_venta)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 8. Producto: catálogo global
-- ============================================================
UPDATE producto
SET estado = 'ACTIVO'
WHERE estado = 'AGOTADO';

SET @fk_producto_sucursal := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'producto'
      AND COLUMN_NAME = 'sucursal_id'
      AND REFERENCED_TABLE_NAME = 'sucursal'
    LIMIT 1
);
SET @sql := IF(
    @fk_producto_sucursal IS NOT NULL,
    CONCAT('ALTER TABLE producto DROP FOREIGN KEY ', @fk_producto_sucursal),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE producto
    DROP COLUMN sucursal_id,
    MODIFY COLUMN estado ENUM('ACTIVO', 'INACTIVO', 'ARCHIVADO') NOT NULL DEFAULT 'ACTIVO';

-- ============================================================
-- 9. Producto variante: catálogo global + índices globales
-- ============================================================
UPDATE producto_variante
SET estado = CASE
    WHEN deleted_at IS NULL THEN 'ACTIVO'
    ELSE 'INACTIVO'
END;

SET @fk_variante_sucursal := (
    SELECT CONSTRAINT_NAME
    FROM information_schema.KEY_COLUMN_USAGE
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'producto_variante'
      AND COLUMN_NAME = 'sucursal_id'
      AND REFERENCED_TABLE_NAME = 'sucursal'
    LIMIT 1
);
SET @sql := IF(
    @fk_variante_sucursal IS NOT NULL,
    CONCAT('ALTER TABLE producto_variante DROP FOREIGN KEY ', @fk_variante_sucursal),
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE producto_variante
    DROP INDEX uk_variante_sucursal_sku,
    DROP INDEX uk_variante_sucursal_codigo_barras,
    DROP INDEX uk_variante_unica,
    DROP COLUMN sucursal_id,
    DROP COLUMN stock,
    ADD UNIQUE KEY uk_variante_sku (sku),
    ADD UNIQUE KEY uk_variante_unica_global (producto_id, talla_id, color_id),
    MODIFY COLUMN estado ENUM('ACTIVO', 'INACTIVO') NOT NULL DEFAULT 'ACTIVO';

SET @idx_variante_codigo_global := (
    SELECT COUNT(*)
    FROM information_schema.STATISTICS
    WHERE TABLE_SCHEMA = DATABASE()
      AND TABLE_NAME = 'producto_variante'
      AND INDEX_NAME = 'uk_variante_codigo_barras'
);
SET @sql := IF(
    @idx_variante_codigo_global = 0,
    'ALTER TABLE producto_variante ADD UNIQUE KEY uk_variante_codigo_barras (codigo_barras)',
    'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- ============================================================
-- 10. Sucursal sin datos fiscales
-- ============================================================
ALTER TABLE sucursal
    DROP COLUMN direccion,
    DROP COLUMN ubigeo,
    DROP COLUMN departamento,
    DROP COLUMN provincia,
    DROP COLUMN distrito,
    DROP COLUMN codigo_establecimiento_sunat;

-- ============================================================
-- 11. Verificación recomendada
-- ============================================================
-- SELECT * FROM empresa LIMIT 1;
-- SELECT id_sucursal, nombre, tipo FROM sucursal;
-- SELECT id_categoria, nombre_categoria FROM categoria ORDER BY nombre_categoria;
-- SELECT COUNT(*) FROM canal_venta;
-- SELECT COUNT(*) FROM sucursal_stock;
-- DESCRIBE producto;
-- DESCRIBE producto_variante;
