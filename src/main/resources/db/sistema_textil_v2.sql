SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

CREATE DATABASE IF NOT EXISTS sistema_textil CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE sistema_textil;

-- =========================
-- EMPRESA
-- =========================
CREATE TABLE IF NOT EXISTS empresa (
  id_empresa INT(11) NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(100) NOT NULL,
  nombre_comercial VARCHAR(150) DEFAULT NULL,
  razon_social VARCHAR(150) NOT NULL,
  ruc VARCHAR(11) NOT NULL,
  correo VARCHAR(150) DEFAULT NULL,
  telefono VARCHAR(20) DEFAULT NULL,
  logo_url VARCHAR(600) NULL,
  genera_facturacion_electronica TINYINT(1) NOT NULL DEFAULT 0,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_empresa),
  UNIQUE KEY uk_empresa_ruc (ruc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- SUNAT CONFIG
-- =========================
CREATE TABLE IF NOT EXISTS sunat_config (
  id_sunat_config INT(11) NOT NULL AUTO_INCREMENT,
  id_empresa INT(11) NOT NULL,
  ambiente ENUM('BETA','PRODUCCION') NOT NULL DEFAULT 'BETA',
  usuario_sol VARCHAR(50) NOT NULL,
  clave_sol VARCHAR(255) NOT NULL,
  url_bill_service VARCHAR(255) DEFAULT NULL,
  certificado_url VARCHAR(600) DEFAULT NULL,
  certificado_password VARCHAR(255) DEFAULT NULL,
  client_id VARCHAR(255) DEFAULT NULL,
  client_secret VARCHAR(255) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_sunat_config),
  UNIQUE KEY uk_sunat_config_empresa_ambiente (id_empresa, ambiente),
  KEY idx_sunat_config_empresa (id_empresa),
  CONSTRAINT fk_sunat_config_empresa
    FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- SUCURSAL
-- =========================
CREATE TABLE IF NOT EXISTS sucursal (
  id_sucursal INT(11) NOT NULL AUTO_INCREMENT,
  id_empresa INT(11) NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  descripcion VARCHAR(255) DEFAULT NULL,
  direccion VARCHAR(255) NOT NULL,
  telefono VARCHAR(20) DEFAULT NULL,
  correo VARCHAR(150) DEFAULT NULL,
  ubigeo VARCHAR(6) DEFAULT NULL,
  departamento VARCHAR(100) DEFAULT NULL,
  provincia VARCHAR(100) DEFAULT NULL,
  distrito VARCHAR(100) DEFAULT NULL,
  codigo_establecimiento_sunat VARCHAR(4) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_sucursal),
  UNIQUE KEY uk_sucursal_empresa_nombre (id_empresa, nombre),
  KEY idx_sucursal_empresa (id_empresa),
  CONSTRAINT fk_sucursal_empresa
    FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- USUARIO
-- =========================
CREATE TABLE IF NOT EXISTS usuario (
  id_usuario INT(11) NOT NULL AUTO_INCREMENT,
  id_sucursal INT(11),
  nombre VARCHAR(80) NOT NULL,
  apellido VARCHAR(80) NOT NULL,
  correo VARCHAR(150) NOT NULL,
  dni VARCHAR(8) NOT NULL,
  telefono VARCHAR(15) NOT NULL,
  password VARCHAR(255) NOT NULL,
  foto_perfil_url VARCHAR(500) DEFAULT NULL,
  rol ENUM('ADMINISTRADOR','VENTAS','ALMACEN') NOT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_usuario),
  UNIQUE KEY uk_usuario_correo (correo),
  UNIQUE KEY uk_usuario_dni (dni),
  UNIQUE KEY uk_usuario_telefono (telefono),
  KEY idx_usuario_sucursal (id_sucursal),
  CONSTRAINT fk_usuario_sucursal
    FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- CATEGORIA
-- =========================
CREATE TABLE categoria(
  id_categoria INT AUTO_INCREMENT PRIMARY KEY,
  id_sucursal INT NOT NULL,
  nombre_categoria VARCHAR(100) NOT NULL,
  descripcion VARCHAR(255),
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6),
  UNIQUE KEY uk_categoria_sucursal_nombre(id_sucursal,nombre_categoria),
  FOREIGN KEY(id_sucursal) REFERENCES sucursal(id_sucursal)
) ENGINE=InnoDB;
-- =========================
-- TALLAS
-- =========================
CREATE TABLE tallas(
  talla_id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(20) NOT NULL UNIQUE,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6)
) ENGINE=InnoDB;
-- =========================
-- COLORES
-- =========================
CREATE TABLE colores(
  color_id INT AUTO_INCREMENT PRIMARY KEY,
  nombre VARCHAR(50) NOT NULL UNIQUE,
  codigo VARCHAR(20),
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6)
) ENGINE=InnoDB;
-- =========================
-- PRODUCTO
-- =========================
CREATE TABLE producto(
  producto_id INT AUTO_INCREMENT PRIMARY KEY,
  sucursal_id INT NOT NULL,
  categoria_id INT NOT NULL,
  nombre VARCHAR(150) NOT NULL,
  descripcion VARCHAR(500),
  estado ENUM('ACTIVO','AGOTADO','ARCHIVADO') NOT NULL DEFAULT 'ACTIVO',
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6),
  KEY idx_producto_sucursal(sucursal_id),
  FOREIGN KEY(sucursal_id) REFERENCES sucursal(id_sucursal),
  FOREIGN KEY(categoria_id) REFERENCES categoria(id_categoria)
) ENGINE=InnoDB;
-- =========================
-- PRODUCTO COLOR IMAGEN
-- =========================
CREATE TABLE producto_color_imagen(
  id_color_imagen INT AUTO_INCREMENT PRIMARY KEY,
  producto_id INT NOT NULL,
  color_id INT NOT NULL,
  url VARCHAR(600) NOT NULL,
  url_thumb VARCHAR(600),
  orden INT NOT NULL DEFAULT 1,
  es_principal TINYINT(1) NOT NULL DEFAULT 0,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6),
  KEY idx_pci_producto_color(producto_id,color_id),
  FOREIGN KEY(producto_id) REFERENCES producto(producto_id),
  FOREIGN KEY(color_id) REFERENCES colores(color_id)
) ENGINE=InnoDB;

-- =========================
-- PRODUCTO VARIANTE
-- =========================
CREATE TABLE producto_variante(
  id_producto_variante INT AUTO_INCREMENT PRIMARY KEY,
  producto_id INT NOT NULL,
  sucursal_id INT NOT NULL,
  talla_id INT NOT NULL,
  color_id INT NOT NULL,
  sku VARCHAR(100) NOT NULL,
  codigo_barras VARCHAR(100),
  precio DECIMAL(10,2) NOT NULL,
  precio_mayor DECIMAL(10,2),
  precio_oferta DECIMAL(10,2),
  oferta_inicio DATETIME(6),
  oferta_fin DATETIME(6),
  stock INT NOT NULL DEFAULT 0,
  estado ENUM('ACTIVO','AGOTADO') NOT NULL DEFAULT 'ACTIVO',
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6),
  UNIQUE KEY uk_variante_sucursal_sku(sucursal_id,sku),
  UNIQUE KEY uk_variante_sucursal_codigo_barras(sucursal_id,codigo_barras),
  UNIQUE KEY uk_variante_unica(producto_id,sucursal_id,talla_id,color_id),
  FOREIGN KEY(producto_id) REFERENCES producto(producto_id),
  FOREIGN KEY(sucursal_id) REFERENCES sucursal(id_sucursal),
  FOREIGN KEY(talla_id) REFERENCES tallas(talla_id),
  FOREIGN KEY(color_id) REFERENCES colores(color_id)
) ENGINE=InnoDB;

SET @col_producto_variante_precio_mayor := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'producto_variante'
    AND COLUMN_NAME = 'precio_mayor'
);
SET @sql := IF(
  @col_producto_variante_precio_mayor = 0,
  'ALTER TABLE producto_variante ADD COLUMN precio_mayor DECIMAL(10,2) DEFAULT NULL AFTER precio',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_producto_variante_codigo_barras := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'producto_variante'
    AND COLUMN_NAME = 'codigo_barras'
);
SET @sql := IF(
  @col_producto_variante_codigo_barras = 0,
  'ALTER TABLE producto_variante ADD COLUMN codigo_barras VARCHAR(100) DEFAULT NULL AFTER sku',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_producto_variante_codigo_barras := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'producto_variante'
    AND INDEX_NAME = 'uk_variante_sucursal_codigo_barras'
);
SET @sql := IF(
  @idx_producto_variante_codigo_barras = 0,
  'ALTER TABLE producto_variante ADD UNIQUE KEY uk_variante_sucursal_codigo_barras (sucursal_id, codigo_barras)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =========================
-- CLIENTE
-- =========================
CREATE TABLE IF NOT EXISTS cliente (
  id_cliente INT(11) NOT NULL AUTO_INCREMENT,
  id_empresa INT(11) NOT NULL,
  id_usuario_creacion INT(11) NOT NULL,
  tipo_documento ENUM('DNI','RUC','CE','SIN_DOC') NOT NULL DEFAULT 'SIN_DOC',
  nro_documento VARCHAR(20) DEFAULT NULL,
  nombres VARCHAR(150) NOT NULL,
  telefono VARCHAR(20) DEFAULT NULL,
  correo VARCHAR(150) DEFAULT NULL,
  direccion VARCHAR(255) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_cliente),
  UNIQUE KEY uk_cliente_empresa_telefono (id_empresa, telefono),
  KEY idx_cliente_empresa (id_empresa),
  KEY idx_cliente_doc (tipo_documento, nro_documento),
  KEY idx_cliente_usuario_creacion (id_usuario_creacion),
  CONSTRAINT fk_cliente_empresa
    FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_cliente_usuario_creacion
    FOREIGN KEY (id_usuario_creacion) REFERENCES usuario (id_usuario)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- MIGRACION: ELIMINAR CAJA/CAJA_SESION (SI EXISTE)
-- =========================
SET @fk_venta_caja := (
  SELECT CONSTRAINT_NAME
  FROM information_schema.TABLE_CONSTRAINTS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND CONSTRAINT_NAME = 'fk_venta_caja_sesion'
    AND CONSTRAINT_TYPE = 'FOREIGN KEY'
  LIMIT 1
);
SET @sql := IF(
  @fk_venta_caja IS NULL,
  'SELECT 1',
  'ALTER TABLE venta DROP FOREIGN KEY fk_venta_caja_sesion'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_venta_caja := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND INDEX_NAME = 'idx_venta_caja_sesion'
);
SET @sql := IF(
  @idx_venta_caja = 0,
  'SELECT 1',
  'ALTER TABLE venta DROP INDEX idx_venta_caja_sesion'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_venta_caja := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND COLUMN_NAME = 'id_caja_sesion'
);
SET @sql := IF(
  @col_venta_caja = 0,
  'SELECT 1',
  'ALTER TABLE venta DROP COLUMN id_caja_sesion'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

DROP TABLE IF EXISTS caja_sesion;
DROP TABLE IF EXISTS caja;

-- =========================
-- COMPROBANTE CONFIG
-- =========================
CREATE TABLE IF NOT EXISTS comprobante_config (
  id_comprobante INT(11) NOT NULL AUTO_INCREMENT,
  id_sucursal INT(11) NOT NULL,
  tipo_comprobante ENUM('NOTA DE VENTA','BOLETA','FACTURA','NOTA_CREDITO_BOLETA','NOTA_CREDITO_FACTURA','COTIZACION') NOT NULL,
  serie VARCHAR(10) NOT NULL,
  ultimo_correlativo INT(11) NOT NULL DEFAULT 0,
  habilitado_venta TINYINT(1) NOT NULL DEFAULT 1,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_comprobante),
  UNIQUE KEY uk_comprobante_config_sucursal_tipo_serie (id_sucursal, tipo_comprobante, serie),
  KEY idx_comprobante_config_lookup (id_sucursal, tipo_comprobante, activo, deleted_at),
  CONSTRAINT fk_comprobante_config_sucursal
    FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET @col_cc_estado := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'comprobante_config'
    AND COLUMN_NAME = 'estado'
);
SET @sql := IF(
  @col_cc_estado = 0,
  'SELECT 1',
  'ALTER TABLE comprobante_config DROP COLUMN estado'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_cc_habilitado_venta := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'comprobante_config'
    AND COLUMN_NAME = 'habilitado_venta'
);
SET @sql := IF(
  @col_cc_habilitado_venta = 0,
  'ALTER TABLE comprobante_config ADD COLUMN habilitado_venta TINYINT(1) NOT NULL DEFAULT 1 AFTER ultimo_correlativo',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_cc_old_unique := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'comprobante_config'
    AND INDEX_NAME = 'uk_comprobante_config_sucursal_tipo'
);
SET @sql := IF(
  @idx_cc_old_unique = 0,
  'SELECT 1',
  'ALTER TABLE comprobante_config DROP INDEX uk_comprobante_config_sucursal_tipo'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_cc_unique_tipo_serie := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'comprobante_config'
    AND INDEX_NAME = 'uk_comprobante_config_sucursal_tipo_serie'
);
SET @sql := IF(
  @idx_cc_unique_tipo_serie = 0,
  'ALTER TABLE comprobante_config ADD UNIQUE KEY uk_comprobante_config_sucursal_tipo_serie (id_sucursal, tipo_comprobante, serie)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

ALTER TABLE comprobante_config
MODIFY COLUMN tipo_comprobante ENUM('NOTA DE VENTA','BOLETA','FACTURA','NOTA_CREDITO_BOLETA','NOTA_CREDITO_FACTURA','COTIZACION') NOT NULL;

UPDATE comprobante_config
SET habilitado_venta = CASE
  WHEN tipo_comprobante IN ('NOTA DE VENTA', 'BOLETA', 'FACTURA') THEN 1
  ELSE 0
END;

-- =========================
-- VENTA
-- =========================
CREATE TABLE IF NOT EXISTS venta (
  id_venta INT(11) NOT NULL AUTO_INCREMENT,
  id_sucursal INT(11) NOT NULL,
  id_usuario INT(11) NOT NULL,
  id_cliente INT(11) DEFAULT NULL,
  tipo_comprobante ENUM('NOTA DE VENTA','BOLETA','FACTURA') NOT NULL DEFAULT 'NOTA DE VENTA',
  serie VARCHAR(10) NOT NULL,
  correlativo INT(11) NOT NULL,
  moneda CHAR(3) NOT NULL DEFAULT 'PEN',
  forma_pago VARCHAR(10) NOT NULL DEFAULT 'CONTADO',
  igv_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 18.00,
  subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  descuento_total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  tipo_descuento ENUM('MONTO','PORCENTAJE') DEFAULT NULL,
  igv DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  estado ENUM('EMITIDA','ANULADA','NC_EMITIDA') NOT NULL DEFAULT 'EMITIDA',
  tipo_anulacion VARCHAR(20) DEFAULT NULL,
  motivo_anulacion VARCHAR(255) DEFAULT NULL,
  anulado_at DATETIME(6) DEFAULT NULL,
  id_usuario_anulacion INT(11) DEFAULT NULL,
  sunat_estado VARCHAR(20) NOT NULL DEFAULT 'NO_APLICA',
  sunat_codigo VARCHAR(20) DEFAULT NULL,
  sunat_mensaje VARCHAR(500) DEFAULT NULL,
  sunat_hash VARCHAR(120) DEFAULT NULL,
  sunat_ticket VARCHAR(120) DEFAULT NULL,
  sunat_xml_nombre VARCHAR(180) DEFAULT NULL,
  sunat_xml_key VARCHAR(600) DEFAULT NULL,
  sunat_zip_nombre VARCHAR(180) DEFAULT NULL,
  sunat_cdr_nombre VARCHAR(180) DEFAULT NULL,
  sunat_cdr_key VARCHAR(600) DEFAULT NULL,
  sunat_enviado_at DATETIME(6) DEFAULT NULL,
  sunat_respondido_at DATETIME(6) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  fecha DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_venta),
  KEY idx_venta_sucursal (id_sucursal),
  KEY idx_venta_usuario (id_usuario),
  KEY idx_venta_cliente (id_cliente),
  UNIQUE KEY uk_venta_numero_comprobante (id_sucursal, tipo_comprobante, serie, correlativo),
  CONSTRAINT fk_venta_sucursal
    FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_venta_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_venta_usuario_anulacion
    FOREIGN KEY (id_usuario_anulacion) REFERENCES usuario (id_usuario)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_venta_cliente
    FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- VENTA DETALLE
-- =========================
CREATE TABLE IF NOT EXISTS venta_detalle (
  id_venta_detalle INT(11) NOT NULL AUTO_INCREMENT,
  id_venta INT(11) NOT NULL,
  id_producto_variante INT(11) NOT NULL,
  descripcion VARCHAR(255) DEFAULT NULL,
  cantidad INT(11) NOT NULL,
  unidad_medida VARCHAR(3) NOT NULL DEFAULT 'NIU',
  codigo_tipo_afectacion_igv VARCHAR(2) NOT NULL DEFAULT '10',
  precio_unitario DECIMAL(10,2) NOT NULL,
  descuento DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  igv_detalle DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  subtotal DECIMAL(10,2) NOT NULL,
  total_detalle DECIMAL(10,2) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_venta_detalle),
  KEY idx_venta_detalle_venta (id_venta),
  KEY idx_venta_detalle_variante (id_producto_variante),
  CONSTRAINT fk_venta_detalle_venta
    FOREIGN KEY (id_venta) REFERENCES venta (id_venta)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_venta_detalle_variante
    FOREIGN KEY (id_producto_variante) REFERENCES producto_variante (id_producto_variante)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- NOTA DE CREDITO
-- =========================
CREATE TABLE IF NOT EXISTS nota_credito (
  id_nota_credito INT(11) NOT NULL AUTO_INCREMENT,
  id_venta_referencia INT(11) NOT NULL,
  id_sucursal INT(11) NOT NULL,
  id_usuario INT(11) NOT NULL,
  id_cliente INT(11) DEFAULT NULL,
  tipo_comprobante VARCHAR(20) NOT NULL,
  serie VARCHAR(10) NOT NULL,
  correlativo INT(11) NOT NULL,
  moneda CHAR(3) NOT NULL DEFAULT 'PEN',
  codigo_motivo VARCHAR(5) NOT NULL,
  descripcion_motivo VARCHAR(255) NOT NULL,
  tipo_documento_ref VARCHAR(2) NOT NULL,
  serie_ref VARCHAR(10) NOT NULL,
  correlativo_ref INT(11) NOT NULL,
  igv_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 18.00,
  subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  descuento_total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  igv DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  estado VARCHAR(20) NOT NULL DEFAULT 'EMITIDA',
  sunat_estado VARCHAR(20) NOT NULL DEFAULT 'PENDIENTE',
  sunat_codigo VARCHAR(20) DEFAULT NULL,
  sunat_mensaje VARCHAR(500) DEFAULT NULL,
  sunat_hash VARCHAR(120) DEFAULT NULL,
  sunat_ticket VARCHAR(120) DEFAULT NULL,
  sunat_xml_nombre VARCHAR(180) DEFAULT NULL,
  sunat_xml_key VARCHAR(600) DEFAULT NULL,
  sunat_zip_nombre VARCHAR(180) DEFAULT NULL,
  sunat_cdr_nombre VARCHAR(180) DEFAULT NULL,
  sunat_cdr_key VARCHAR(600) DEFAULT NULL,
  sunat_enviado_at DATETIME(6) DEFAULT NULL,
  sunat_respondido_at DATETIME(6) DEFAULT NULL,
  stock_devuelto TINYINT(1) NOT NULL DEFAULT 0,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  fecha DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_nota_credito),
  KEY idx_nota_credito_venta_ref (id_venta_referencia),
  KEY idx_nota_credito_sucursal (id_sucursal),
  KEY idx_nota_credito_usuario (id_usuario),
  KEY idx_nota_credito_cliente (id_cliente),
  UNIQUE KEY uk_nota_credito_numero_comprobante (id_sucursal, tipo_comprobante, serie, correlativo),
  CONSTRAINT fk_nc_venta_ref
    FOREIGN KEY (id_venta_referencia) REFERENCES venta (id_venta)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_nc_sucursal
    FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_nc_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_nc_cliente
    FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- NOTA DE CREDITO DETALLE
-- =========================
CREATE TABLE IF NOT EXISTS nota_credito_detalle (
  id_nota_credito_detalle INT(11) NOT NULL AUTO_INCREMENT,
  id_nota_credito INT(11) NOT NULL,
  id_producto_variante INT(11) NOT NULL,
  id_venta_detalle_ref INT(11) DEFAULT NULL,
  descripcion VARCHAR(255) DEFAULT NULL,
  cantidad INT(11) NOT NULL,
  unidad_medida VARCHAR(3) NOT NULL DEFAULT 'NIU',
  codigo_tipo_afectacion_igv VARCHAR(2) NOT NULL DEFAULT '10',
  precio_unitario DECIMAL(10,2) NOT NULL,
  descuento DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  igv_detalle DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  subtotal DECIMAL(10,2) NOT NULL,
  total_detalle DECIMAL(10,2) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_nota_credito_detalle),
  KEY idx_ncd_nota_credito (id_nota_credito),
  KEY idx_ncd_producto_variante (id_producto_variante),
  KEY idx_ncd_venta_detalle_ref (id_venta_detalle_ref),
  CONSTRAINT fk_ncd_nota_credito
    FOREIGN KEY (id_nota_credito) REFERENCES nota_credito (id_nota_credito)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_ncd_producto_variante
    FOREIGN KEY (id_producto_variante) REFERENCES producto_variante (id_producto_variante)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_ncd_venta_detalle_ref
    FOREIGN KEY (id_venta_detalle_ref) REFERENCES venta_detalle (id_venta_detalle)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- COTIZACION
-- =========================
CREATE TABLE IF NOT EXISTS cotizacion (
  id_cotizacion INT(11) NOT NULL AUTO_INCREMENT,
  id_sucursal INT(11) NOT NULL,
  id_usuario INT(11) NOT NULL,
  id_cliente INT(11) DEFAULT NULL,
  serie VARCHAR(10) DEFAULT NULL,
  correlativo INT(11) DEFAULT NULL,
  fecha DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  igv_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 18.00,
  subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  descuento_total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  tipo_descuento ENUM('MONTO','PORCENTAJE') DEFAULT NULL,
  igv DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  estado ENUM('ACTIVA','CONVERTIDA') NOT NULL DEFAULT 'ACTIVA',
  observacion VARCHAR(500) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_cotizacion),
  KEY idx_cotizacion_sucursal (id_sucursal),
  KEY idx_cotizacion_usuario (id_usuario),
  KEY idx_cotizacion_cliente (id_cliente),
  UNIQUE KEY uk_cotizacion_numero (id_sucursal, serie, correlativo),
  CONSTRAINT fk_cotizacion_sucursal
    FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_cotizacion_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_cotizacion_cliente
    FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET @sql := 'ALTER TABLE cotizacion MODIFY COLUMN estado ENUM(''BORRADOR'',''ENVIADA'',''APROBADA'',''RECHAZADA'',''VENCIDA'',''ACTIVA'',''CONVERTIDA'') NOT NULL DEFAULT ''ACTIVA''';
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE cotizacion
SET estado = CASE
  WHEN UPPER(TRIM(COALESCE(estado, ''))) = 'CONVERTIDA' THEN 'CONVERTIDA'
  ELSE 'ACTIVA'
END;

SET @idx_cotizacion_vencimiento := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'cotizacion'
    AND INDEX_NAME = 'idx_cotizacion_fecha_vencimiento'
);
SET @sql := IF(
  @idx_cotizacion_vencimiento = 0,
  'SELECT 1',
  'ALTER TABLE cotizacion DROP INDEX idx_cotizacion_fecha_vencimiento'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_cotizacion_vencimiento := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'cotizacion'
    AND COLUMN_NAME = 'fecha_vencimiento'
);
SET @sql := IF(
  @col_cotizacion_vencimiento = 0,
  'SELECT 1',
  'ALTER TABLE cotizacion DROP COLUMN fecha_vencimiento'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @sql := 'ALTER TABLE cotizacion MODIFY COLUMN estado ENUM(''ACTIVA'',''CONVERTIDA'') NOT NULL DEFAULT ''ACTIVA''';
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =========================
-- COTIZACION DETALLE
-- =========================
CREATE TABLE IF NOT EXISTS cotizacion_detalle (
  id_cotizacion_detalle INT(11) NOT NULL AUTO_INCREMENT,
  id_cotizacion INT(11) NOT NULL,
  id_producto_variante INT(11) NOT NULL,
  cantidad INT(11) NOT NULL,
  precio_unitario DECIMAL(10,2) NOT NULL,
  descuento DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  subtotal DECIMAL(10,2) NOT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_cotizacion_detalle),
  KEY idx_cotizacion_detalle_cotizacion (id_cotizacion),
  KEY idx_cotizacion_detalle_variante (id_producto_variante),
  CONSTRAINT fk_cotizacion_detalle_cotizacion
    FOREIGN KEY (id_cotizacion) REFERENCES cotizacion (id_cotizacion)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_cotizacion_detalle_variante
    FOREIGN KEY (id_producto_variante) REFERENCES producto_variante (id_producto_variante)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- METODO PAGO CONFIG
-- =========================
CREATE TABLE IF NOT EXISTS metodo_pago_config (
  id_metodo_pago INT(11) NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(50) NOT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_metodo_pago),
  UNIQUE KEY uk_metodo_pago_nombre (nombre)
) ENGINE=InnoDB;

-- =========================
-- PAGO
-- =========================
CREATE TABLE IF NOT EXISTS pago (
  id_pago INT(11) NOT NULL AUTO_INCREMENT,
  id_venta INT(11) NOT NULL,
  id_metodo_pago INT(11) NOT NULL,
  monto DECIMAL(10,2) NOT NULL,
  codigo_operacion VARCHAR(100) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  fecha DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_pago),
  KEY idx_pago_venta (id_venta),
  KEY idx_pago_metodo (id_metodo_pago),
  CONSTRAINT fk_pago_venta
    FOREIGN KEY (id_venta) REFERENCES venta (id_venta)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_pago_metodo
    FOREIGN KEY (id_metodo_pago) REFERENCES metodo_pago_config (id_metodo_pago)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SET @col_pago_codigo_operacion := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'pago'
    AND COLUMN_NAME = 'codigo_operacion'
);
SET @col_pago_referencia := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'pago'
    AND COLUMN_NAME = 'referencia'
);
SET @sql := IF(
  @col_pago_codigo_operacion > 0,
  'SELECT 1',
  IF(
    @col_pago_referencia = 0,
    'ALTER TABLE pago ADD COLUMN codigo_operacion VARCHAR(100) DEFAULT NULL AFTER monto',
    'ALTER TABLE pago CHANGE COLUMN referencia codigo_operacion VARCHAR(100) DEFAULT NULL'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

-- =========================
-- HISTORIAL STOCK
-- =========================
CREATE TABLE IF NOT EXISTS historial_stock (
  id_historial INT(11) NOT NULL AUTO_INCREMENT,
  fecha DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  tipo_movimiento ENUM('ENTRADA','SALIDA','AJUSTE','VENTA','DEVOLUCION','RESERVA','LIBERACION') NOT NULL,
  motivo VARCHAR(150) DEFAULT NULL,
  id_producto_variante INT(11) NOT NULL,
  id_sucursal INT(11) NOT NULL,
  id_usuario INT(11) NOT NULL,
  cantidad INT(11) NOT NULL,
  stock_anterior INT(11) NOT NULL,
  stock_nuevo INT(11) NOT NULL,
  PRIMARY KEY (id_historial),
  KEY idx_historial_variante (id_producto_variante),
  KEY idx_historial_sucursal (id_sucursal),
  KEY idx_historial_usuario (id_usuario),
  CONSTRAINT fk_historial_variante
    FOREIGN KEY (id_producto_variante) REFERENCES producto_variante (id_producto_variante)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_historial_sucursal
    FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_historial_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- IMPORTACION PRODUCTO HISTORIAL
-- =========================
CREATE TABLE IF NOT EXISTS importacion_producto_historial (
  id_importacion INT(11) NOT NULL AUTO_INCREMENT,
  id_usuario INT(11) NOT NULL,
  id_sucursal INT(11) DEFAULT NULL,
  nombre_archivo VARCHAR(255) NOT NULL,
  tamano_bytes BIGINT NOT NULL,
  filas_procesadas INT(11) NOT NULL DEFAULT 0,
  productos_creados INT(11) NOT NULL DEFAULT 0,
  productos_actualizados INT(11) NOT NULL DEFAULT 0,
  variantes_guardadas INT(11) NOT NULL DEFAULT 0,
  categorias_creadas INT(11) NOT NULL DEFAULT 0,
  colores_creados INT(11) NOT NULL DEFAULT 0,
  tallas_creadas INT(11) NOT NULL DEFAULT 0,
  estado ENUM('EXITOSA','PARCIAL','FALLIDA') NOT NULL DEFAULT 'EXITOSA',
  mensaje_error VARCHAR(1000) DEFAULT NULL,
  duracion_ms INT(11) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_importacion),
  KEY idx_importacion_usuario (id_usuario),
  KEY idx_importacion_sucursal (id_sucursal),
  KEY idx_importacion_fecha (created_at),
  KEY idx_importacion_estado (estado),
  CONSTRAINT fk_importacion_usuario
    FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
    ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_importacion_sucursal
    FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal)
    ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- =========================
-- DATOS INICIALES MINIMOS (EMPRESA + SUCURSAL)
-- =========================
INSERT INTO empresa (
  id_empresa, nombre, nombre_comercial, razon_social, ruc, correo, telefono, genera_facturacion_electronica, activo, created_at, updated_at, deleted_at
) VALUES (
  1, 'Empresa Demo', 'Empresa Demo', 'Empresa Demo S.A.C.', '20123456789', 'empresa@demo.com', '900000000', 0, 1,
  CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL
)
ON DUPLICATE KEY UPDATE
  nombre = VALUES(nombre),
  nombre_comercial = VALUES(nombre_comercial),
  razon_social = VALUES(razon_social),
  correo = VALUES(correo),
  telefono = VALUES(telefono),
  genera_facturacion_electronica = VALUES(genera_facturacion_electronica),
  activo = VALUES(activo),
  updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO sucursal (
  id_sucursal, id_empresa, nombre, descripcion, direccion, telefono, correo, ubigeo, departamento, provincia, distrito, codigo_establecimiento_sunat, activo, created_at, updated_at, deleted_at
) VALUES (
  1, 1, 'Sucursal Principal', 'Sucursal inicial del sistema', 'Direccion principal',
  '900000000', 'sucursal@demo.com', '150101', 'LIMA', 'LIMA', 'LIMA', '0000', 1, CURRENT_TIMESTAMP(6), CURRENT_TIMESTAMP(6), NULL
)
ON DUPLICATE KEY UPDATE
  id_empresa = VALUES(id_empresa),
  nombre = VALUES(nombre),
  descripcion = VALUES(descripcion),
  direccion = VALUES(direccion),
  telefono = VALUES(telefono),
  correo = VALUES(correo),
  ubigeo = VALUES(ubigeo),
  departamento = VALUES(departamento),
  provincia = VALUES(provincia),
  distrito = VALUES(distrito),
  codigo_establecimiento_sunat = VALUES(codigo_establecimiento_sunat),
  activo = VALUES(activo),
  updated_at = CURRENT_TIMESTAMP(6);

UPDATE cotizacion
SET serie = CONCAT('COT', LPAD(id_sucursal, 2, '0'))
WHERE serie IS NULL
   OR TRIM(serie) = ''
   OR UPPER(TRIM(serie)) = 'COT';

UPDATE comprobante_config cc
SET cc.serie = CONCAT('COT', LPAD(cc.id_sucursal, 2, '0'))
WHERE cc.tipo_comprobante = 'COTIZACION'
  AND cc.deleted_at IS NULL
  AND (
    cc.serie IS NULL
    OR TRIM(cc.serie) = ''
    OR UPPER(TRIM(cc.serie)) = 'COT'
  )
  AND NOT EXISTS (
    SELECT 1
    FROM comprobante_config cc2
    WHERE cc2.id_sucursal = cc.id_sucursal
      AND cc2.tipo_comprobante = 'COTIZACION'
      AND cc2.deleted_at IS NULL
      AND cc2.id_comprobante <> cc.id_comprobante
      AND cc2.serie = CONCAT('COT', LPAD(cc.id_sucursal, 2, '0'))
  );

INSERT INTO comprobante_config (
  id_sucursal,
  tipo_comprobante,
  serie,
  ultimo_correlativo,
  habilitado_venta,
  activo,
  created_at,
  updated_at,
  deleted_at
)
SELECT
  s.id_sucursal,
  t.tipo_comprobante,
  CASE
    WHEN t.tipo_comprobante = 'COTIZACION' THEN CONCAT('COT', LPAD(s.id_sucursal, 2, '0'))
    ELSE t.serie
  END,
  0,
  t.habilitado_venta,
  1,
  CURRENT_TIMESTAMP(6),
  CURRENT_TIMESTAMP(6),
  NULL
FROM sucursal s
JOIN (
  SELECT 'NOTA DE VENTA' AS tipo_comprobante, 'NV01' AS serie, 1 AS habilitado_venta
  UNION ALL SELECT 'BOLETA', 'B001', 1
  UNION ALL SELECT 'FACTURA', 'F001', 1
  UNION ALL SELECT 'NOTA_CREDITO_BOLETA', 'BC01', 0
  UNION ALL SELECT 'NOTA_CREDITO_FACTURA', 'FC01', 0
  UNION ALL SELECT 'COTIZACION', 'COT', 0
) t
WHERE s.deleted_at IS NULL
  AND s.activo = 1
ON DUPLICATE KEY UPDATE
  serie = VALUES(serie),
  habilitado_venta = VALUES(habilitado_venta),
  activo = VALUES(activo),
  updated_at = CURRENT_TIMESTAMP(6),
  deleted_at = NULL;

INSERT INTO metodo_pago_config (nombre, activo) VALUES
  ('EFECTIVO', 1),
  ('YAPE', 1),
  ('PLIN', 1),
  ('TARJETA', 0),
  ('TRANSFERENCIA', 0);

-- =========================
-- MIGRACION DATOS DE VENTA (SI EXISTEN)
-- =========================
UPDATE venta
SET tipo_comprobante = 'NOTA DE VENTA'
WHERE tipo_comprobante = 'TICKET' OR tipo_comprobante = '';

DROP TABLE IF EXISTS comunicacion_baja_detalle;
DROP TABLE IF EXISTS comunicacion_baja;

UPDATE venta v
JOIN (
  SELECT cc1.id_sucursal, cc1.tipo_comprobante, cc1.serie
  FROM comprobante_config cc1
  JOIN (
    SELECT id_sucursal, tipo_comprobante, MAX(id_comprobante) AS id_comprobante
    FROM comprobante_config
    WHERE activo = 1
      AND deleted_at IS NULL
    GROUP BY id_sucursal, tipo_comprobante
  ) ultimo
    ON ultimo.id_comprobante = cc1.id_comprobante
) cc
  ON cc.id_sucursal = v.id_sucursal
 AND cc.tipo_comprobante = v.tipo_comprobante
SET v.serie = cc.serie
WHERE v.serie IS NULL OR v.serie = '';

UPDATE venta v
JOIN (
  SELECT
    x.id_venta,
    COALESCE(m.max_corr, 0) AS max_corr,
    x.rn
  FROM (
    SELECT
      id_venta,
      id_sucursal,
      tipo_comprobante,
      serie,
      ROW_NUMBER() OVER (
        PARTITION BY id_sucursal, tipo_comprobante, serie
        ORDER BY fecha, id_venta
      ) AS rn
    FROM venta
    WHERE correlativo IS NULL
  ) x
  LEFT JOIN (
    SELECT
      id_sucursal,
      tipo_comprobante,
      serie,
      MAX(correlativo) AS max_corr
    FROM venta
    WHERE correlativo IS NOT NULL
    GROUP BY id_sucursal, tipo_comprobante, serie
  ) m
    ON m.id_sucursal = x.id_sucursal
   AND m.tipo_comprobante = x.tipo_comprobante
   AND m.serie = x.serie
) calc ON calc.id_venta = v.id_venta
SET v.correlativo = calc.max_corr + calc.rn;

UPDATE comprobante_config cc
LEFT JOIN (
  SELECT
    id_sucursal,
    tipo_comprobante,
    serie,
    COALESCE(MAX(correlativo), 0) AS max_corr
  FROM venta
  WHERE deleted_at IS NULL
  GROUP BY id_sucursal, tipo_comprobante, serie
) v
  ON v.id_sucursal = cc.id_sucursal
 AND v.tipo_comprobante = cc.tipo_comprobante
 AND v.serie = cc.serie
SET cc.ultimo_correlativo = COALESCE(v.max_corr, 0),
    cc.updated_at = CURRENT_TIMESTAMP(6)
WHERE cc.deleted_at IS NULL;

UPDATE comprobante_config cc
LEFT JOIN (
  SELECT
    id_sucursal,
    COALESCE(NULLIF(TRIM(serie), ''), CONCAT('COT', LPAD(id_sucursal, 2, '0'))) AS serie_normalizada,
    COALESCE(MAX(correlativo), 0) AS max_corr
  FROM cotizacion
  WHERE deleted_at IS NULL
  GROUP BY id_sucursal, COALESCE(NULLIF(TRIM(serie), ''), CONCAT('COT', LPAD(id_sucursal, 2, '0')))
) c
  ON c.id_sucursal = cc.id_sucursal
 AND c.serie_normalizada = cc.serie
SET cc.ultimo_correlativo = GREATEST(COALESCE(cc.ultimo_correlativo, 0), COALESCE(c.max_corr, 0)),
    cc.habilitado_venta = 0,
    cc.updated_at = CURRENT_TIMESTAMP(6)
WHERE cc.deleted_at IS NULL
  AND cc.tipo_comprobante = 'COTIZACION';

ALTER TABLE venta MODIFY COLUMN serie VARCHAR(10) NOT NULL;
ALTER TABLE venta MODIFY COLUMN correlativo INT(11) NOT NULL;
ALTER TABLE venta MODIFY COLUMN estado ENUM('EMITIDA','ANULADA','NC_EMITIDA') NOT NULL DEFAULT 'EMITIDA';

SET @idx_uk_nota_credito_numero := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'nota_credito'
    AND INDEX_NAME = 'uk_nota_credito_numero_comprobante'
);
SET @sql := IF(
  @idx_uk_nota_credito_numero = 0,
  'ALTER TABLE nota_credito ADD UNIQUE KEY uk_nota_credito_numero_comprobante (id_sucursal, tipo_comprobante, serie, correlativo)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @idx_uk_venta_numero := (
  SELECT COUNT(*)
  FROM information_schema.STATISTICS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND INDEX_NAME = 'uk_venta_numero_comprobante'
);
SET @sql := IF(
  @idx_uk_venta_numero = 0,
  'ALTER TABLE venta ADD UNIQUE KEY uk_venta_numero_comprobante (id_sucursal, tipo_comprobante, serie, correlativo)',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_usuario_foto_perfil_url := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'usuario'
    AND COLUMN_NAME = 'foto_perfil_url'
);
SET @sql := IF(
  @col_usuario_foto_perfil_url = 0,
  'ALTER TABLE usuario ADD COLUMN foto_perfil_url VARCHAR(500) DEFAULT NULL AFTER password',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
SET @col_venta_forma_pago := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND COLUMN_NAME = 'forma_pago'
);
SET @sql := IF(
  @col_venta_forma_pago = 0,
  'ALTER TABLE venta ADD COLUMN forma_pago VARCHAR(10) NOT NULL DEFAULT ''CONTADO'' AFTER moneda',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_venta_tipo_anulacion := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND COLUMN_NAME = 'tipo_anulacion'
);
SET @col_venta_anulacion_tipo := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND COLUMN_NAME = 'anulacion_tipo'
);
SET @sql := IF(
  @col_venta_tipo_anulacion > 0,
  'SELECT 1',
  IF(
    @col_venta_anulacion_tipo > 0,
    'ALTER TABLE venta CHANGE COLUMN anulacion_tipo tipo_anulacion VARCHAR(20) DEFAULT NULL',
    'ALTER TABLE venta ADD COLUMN tipo_anulacion VARCHAR(20) DEFAULT NULL AFTER estado'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_venta_motivo_anulacion := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND COLUMN_NAME = 'motivo_anulacion'
);
SET @col_venta_anulacion_motivo := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND COLUMN_NAME = 'anulacion_motivo'
);
SET @sql := IF(
  @col_venta_motivo_anulacion > 0,
  'SELECT 1',
  IF(
    @col_venta_anulacion_motivo > 0,
    'ALTER TABLE venta CHANGE COLUMN anulacion_motivo motivo_anulacion VARCHAR(255) DEFAULT NULL',
    'ALTER TABLE venta ADD COLUMN motivo_anulacion VARCHAR(255) DEFAULT NULL AFTER tipo_anulacion'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_venta_anulado_at := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND COLUMN_NAME = 'anulado_at'
);
SET @col_venta_anulacion_fecha := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND COLUMN_NAME = 'anulacion_fecha'
);
SET @sql := IF(
  @col_venta_anulado_at > 0,
  'SELECT 1',
  IF(
    @col_venta_anulacion_fecha > 0,
    'ALTER TABLE venta CHANGE COLUMN anulacion_fecha anulado_at DATETIME(6) DEFAULT NULL',
    'ALTER TABLE venta ADD COLUMN anulado_at DATETIME(6) DEFAULT NULL AFTER motivo_anulacion'
  )
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @col_venta_id_usuario_anulacion := (
  SELECT COUNT(*)
  FROM information_schema.COLUMNS
  WHERE TABLE_SCHEMA = DATABASE()
    AND TABLE_NAME = 'venta'
    AND COLUMN_NAME = 'id_usuario_anulacion'
);
SET @sql := IF(
  @col_venta_id_usuario_anulacion = 0,
  'ALTER TABLE venta ADD COLUMN id_usuario_anulacion INT(11) DEFAULT NULL AFTER anulado_at',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @fk_venta_usuario_anulacion := (
  SELECT COUNT(*)
  FROM information_schema.REFERENTIAL_CONSTRAINTS
  WHERE CONSTRAINT_SCHEMA = DATABASE()
    AND CONSTRAINT_NAME = 'fk_venta_usuario_anulacion'
    AND TABLE_NAME = 'venta'
);
SET @sql := IF(
  @fk_venta_usuario_anulacion = 0,
  'ALTER TABLE venta ADD CONSTRAINT fk_venta_usuario_anulacion FOREIGN KEY (id_usuario_anulacion) REFERENCES usuario (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT',
  'SELECT 1'
);
PREPARE stmt FROM @sql;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE venta
SET estado = 'ANULADA'
WHERE estado = 'ANULACION_PENDIENTE';

UPDATE venta
SET estado = 'ANULADA'
WHERE estado = 'NC_EMITIDA';

-- =========================
-- NORMALIZACION CATALOGO
-- =========================
UPDATE producto
SET deleted_at = COALESCE(deleted_at, CURRENT_TIMESTAMP(6)),
    activo = 0,
    estado = 'ACTIVO'
WHERE estado = 'ARCHIVADO'
   OR deleted_at IS NOT NULL;

UPDATE producto
SET activo = 1,
    estado = 'ACTIVO'
WHERE deleted_at IS NULL;

UPDATE producto_variante
SET activo = CASE
        WHEN deleted_at IS NULL THEN 1
        ELSE 0
    END,
    estado = CASE
        WHEN stock <= 0 THEN 'AGOTADO'
        ELSE 'ACTIVO'
    END;

COMMIT;
