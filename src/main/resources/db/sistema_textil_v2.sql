SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";

CREATE DATABASE IF NOT EXISTS sistema_textil CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci;
USE sistema_textil;

-- EMPRESA
CREATE TABLE empresa (
  id_empresa INT(11) NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(100) NOT NULL,
  razon_social VARCHAR(150) NOT NULL,
  ruc VARCHAR(11) NOT NULL,
  correo VARCHAR(150) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_empresa),
  UNIQUE KEY uk_empresa_ruc (ruc)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- SUCURSAL
CREATE TABLE sucursal (
  id_sucursal INT(11) NOT NULL AUTO_INCREMENT,
  id_empresa INT(11) NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  descripcion VARCHAR(255) DEFAULT NULL,
  direccion VARCHAR(255) NOT NULL,
  telefono VARCHAR(20) DEFAULT NULL,
  correo VARCHAR(150) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_sucursal),
  UNIQUE KEY uk_sucursal_empresa_nombre (id_empresa, nombre),
  KEY idx_sucursal_empresa (id_empresa),
  CONSTRAINT fk_sucursal_empresa FOREIGN KEY (id_empresa) REFERENCES empresa (id_empresa) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- USUARIO
CREATE TABLE usuario (
  id_usuario INT(11) NOT NULL AUTO_INCREMENT,
  id_sucursal INT(11) NOT NULL,
  nombre VARCHAR(80) NOT NULL,
  apellido VARCHAR(80) NOT NULL,
  correo VARCHAR(150) NOT NULL,
  dni VARCHAR(8) NOT NULL,
  telefono VARCHAR(15) NOT NULL,
  password VARCHAR(255) NOT NULL,
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
  CONSTRAINT fk_usuario_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- CATEGORIA
CREATE TABLE categoria (
  id_categoria INT(11) NOT NULL AUTO_INCREMENT,
  id_sucursal INT(11) NOT NULL,
  nombre_categoria VARCHAR(100) NOT NULL,
  descripcion VARCHAR(255) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_categoria),
  UNIQUE KEY uk_categoria_sucursal_nombre (id_sucursal, nombre_categoria),
  KEY idx_categoria_sucursal (id_sucursal),
  CONSTRAINT fk_categoria_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- TALLAS
CREATE TABLE tallas (
  talla_id INT(11) NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(20) NOT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (talla_id),
  UNIQUE KEY uk_talla_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- COLORES
CREATE TABLE colores (
  color_id INT(11) NOT NULL AUTO_INCREMENT,
  nombre VARCHAR(50) NOT NULL,
  codigo VARCHAR(20) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (color_id),
  UNIQUE KEY uk_color_nombre (nombre)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- PRODUCTO
CREATE TABLE producto (
  producto_id INT(11) NOT NULL AUTO_INCREMENT,
  sucursal_id INT(11) NOT NULL,
  categoria_id INT(11) NOT NULL,
  nombre VARCHAR(150) NOT NULL,
  descripcion VARCHAR(255) DEFAULT NULL,
  sku VARCHAR(100) NOT NULL,
  codigo_externo VARCHAR(100) DEFAULT NULL,
  imagen_url VARCHAR(500) DEFAULT NULL,
  estado ENUM('ACTIVO','AGOTADO','ARCHIVADO') NOT NULL DEFAULT 'ACTIVO',
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (producto_id),
  UNIQUE KEY uk_producto_sucursal_sku (sucursal_id, sku),
  UNIQUE KEY uk_producto_codigo_externo (codigo_externo),
  KEY idx_producto_sucursal (sucursal_id),
  KEY idx_producto_categoria (categoria_id),
  CONSTRAINT fk_producto_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal (id_sucursal) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_producto_categoria FOREIGN KEY (categoria_id) REFERENCES categoria (id_categoria) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- PRODUCTO VARIANTE
CREATE TABLE producto_variante (
  id_producto_variante INT(11) NOT NULL AUTO_INCREMENT,
  producto_id INT(11) NOT NULL,
  sucursal_id INT(11) NOT NULL,
  talla_id INT(11) NOT NULL,
  color_id INT(11) NOT NULL,
  precio DECIMAL(10,2) NOT NULL,
  stock INT(11) NOT NULL DEFAULT 0,
  estado ENUM('ACTIVO','AGOTADO') NOT NULL DEFAULT 'ACTIVO',
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_producto_variante),
  UNIQUE KEY uk_variante_unica (producto_id, sucursal_id, talla_id, color_id),
  KEY idx_variante_producto (producto_id),
  KEY idx_variante_sucursal (sucursal_id),
  KEY idx_variante_talla (talla_id),
  KEY idx_variante_color (color_id),
  CONSTRAINT fk_variante_producto FOREIGN KEY (producto_id) REFERENCES producto (producto_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_variante_sucursal FOREIGN KEY (sucursal_id) REFERENCES sucursal (id_sucursal) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_variante_talla FOREIGN KEY (talla_id) REFERENCES tallas (talla_id) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_variante_color FOREIGN KEY (color_id) REFERENCES colores (color_id) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- CLIENTE
CREATE TABLE cliente (
  id_cliente INT(11) NOT NULL AUTO_INCREMENT,
  id_sucursal INT(11) NOT NULL,
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
  KEY idx_cliente_sucursal (id_sucursal),
  KEY idx_cliente_doc (tipo_documento, nro_documento),
  CONSTRAINT fk_cliente_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- CAJA
CREATE TABLE caja (
  id_caja INT(11) NOT NULL AUTO_INCREMENT,
  id_sucursal INT(11) NOT NULL,
  nombre VARCHAR(100) NOT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_caja),
  UNIQUE KEY uk_caja_sucursal_nombre (id_sucursal, nombre),
  KEY idx_caja_sucursal (id_sucursal),
  CONSTRAINT fk_caja_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- CAJA SESION
CREATE TABLE caja_sesion (
  id_caja_sesion INT(11) NOT NULL AUTO_INCREMENT,
  id_caja INT(11) NOT NULL,
  id_usuario_apertura INT(11) NOT NULL,
  fecha_apertura DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  monto_apertura DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  fecha_cierre DATETIME(6) DEFAULT NULL,
  id_usuario_cierre INT(11) DEFAULT NULL,
  monto_cierre DECIMAL(10,2) DEFAULT NULL,
  estado ENUM('ABIERTA','CERRADA') NOT NULL DEFAULT 'ABIERTA',
  PRIMARY KEY (id_caja_sesion),
  KEY idx_caja_sesion_caja (id_caja),
  KEY idx_caja_sesion_usuario_apertura (id_usuario_apertura),
  KEY idx_caja_sesion_usuario_cierre (id_usuario_cierre),
  CONSTRAINT fk_caja_sesion_caja FOREIGN KEY (id_caja) REFERENCES caja (id_caja) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_caja_sesion_usuario_apertura FOREIGN KEY (id_usuario_apertura) REFERENCES usuario (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_caja_sesion_usuario_cierre FOREIGN KEY (id_usuario_cierre) REFERENCES usuario (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- VENTA
CREATE TABLE venta (
  id_venta INT(11) NOT NULL AUTO_INCREMENT,
  id_sucursal INT(11) NOT NULL,
  id_usuario INT(11) NOT NULL,
  id_cliente INT(11) DEFAULT NULL,
  id_caja_sesion INT(11) NOT NULL,
  tipo_comprobante ENUM('TICKET','BOLETA','FACTURA') NOT NULL DEFAULT 'TICKET',
  serie VARCHAR(10) DEFAULT NULL,
  correlativo INT(11) DEFAULT NULL,
  igv_porcentaje DECIMAL(5,2) NOT NULL DEFAULT 18.00,
  subtotal DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  descuento_total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  tipo_descuento ENUM('MONTO','PORCENTAJE') DEFAULT NULL,
  igv DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  total DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  estado ENUM('EMITIDA','ANULADA') NOT NULL DEFAULT 'EMITIDA',
  activo TINYINT(1) NOT NULL DEFAULT 1,
  fecha DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_venta),
  KEY idx_venta_sucursal (id_sucursal),
  KEY idx_venta_usuario (id_usuario),
  KEY idx_venta_cliente (id_cliente),
  KEY idx_venta_caja_sesion (id_caja_sesion),
  CONSTRAINT fk_venta_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_venta_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_venta_cliente FOREIGN KEY (id_cliente) REFERENCES cliente (id_cliente) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_venta_caja_sesion FOREIGN KEY (id_caja_sesion) REFERENCES caja_sesion (id_caja_sesion) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- VENTA DETALLE
CREATE TABLE venta_detalle (
  id_venta_detalle INT(11) NOT NULL AUTO_INCREMENT,
  id_venta INT(11) NOT NULL,
  id_producto_variante INT(11) NOT NULL,
  cantidad INT(11) NOT NULL,
  precio_unitario DECIMAL(10,2) NOT NULL,
  descuento DECIMAL(10,2) NOT NULL DEFAULT 0.00,
  subtotal DECIMAL(10,2) NOT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_venta_detalle),
  KEY idx_venta_detalle_venta (id_venta),
  KEY idx_venta_detalle_variante (id_producto_variante),
  CONSTRAINT fk_venta_detalle_venta FOREIGN KEY (id_venta) REFERENCES venta (id_venta) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_venta_detalle_variante FOREIGN KEY (id_producto_variante) REFERENCES producto_variante (id_producto_variante) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- PAGO
CREATE TABLE pago (
  id_pago INT(11) NOT NULL AUTO_INCREMENT,
  id_venta INT(11) NOT NULL,
  metodo ENUM('EFECTIVO','YAPE','PLIN','TARJETA','TRANSFERENCIA') NOT NULL,
  monto DECIMAL(10,2) NOT NULL,
  referencia VARCHAR(100) DEFAULT NULL,
  activo TINYINT(1) NOT NULL DEFAULT 1,
  fecha DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
  updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
  deleted_at DATETIME(6) DEFAULT NULL,
  PRIMARY KEY (id_pago),
  KEY idx_pago_venta (id_venta),
  CONSTRAINT fk_pago_venta FOREIGN KEY (id_venta) REFERENCES venta (id_venta) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- HISTORIAL STOCK
CREATE TABLE historial_stock (
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
  CONSTRAINT fk_historial_variante FOREIGN KEY (id_producto_variante) REFERENCES producto_variante (id_producto_variante) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_historial_sucursal FOREIGN KEY (id_sucursal) REFERENCES sucursal (id_sucursal) ON DELETE RESTRICT ON UPDATE RESTRICT,
  CONSTRAINT fk_historial_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario) ON DELETE RESTRICT ON UPDATE RESTRICT
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- DATOS INICIALES MINIMOS (EMPRESA + SUCURSAL)
-- Permite registrar usuarios cuando id_sucursal es obligatorio.
INSERT INTO empresa (
  id_empresa,
  nombre,
  razon_social,
  ruc,
  correo,
  activo,
  created_at,
  updated_at,
  deleted_at
) VALUES (
  1,
  'Empresa Demo',
  'Empresa Demo S.A.C.',
  '20123456789',
  'empresa@demo.com',
  1,
  CURRENT_TIMESTAMP(6),
  CURRENT_TIMESTAMP(6),
  NULL
)
ON DUPLICATE KEY UPDATE
  nombre = VALUES(nombre),
  razon_social = VALUES(razon_social),
  correo = VALUES(correo),
  activo = VALUES(activo),
  updated_at = CURRENT_TIMESTAMP(6);

INSERT INTO sucursal (
  id_sucursal,
  id_empresa,
  nombre,
  descripcion,
  direccion,
  telefono,
  correo,
  activo,
  created_at,
  updated_at,
  deleted_at
) VALUES (
  1,
  1,
  'Sucursal Principal',
  'Sucursal inicial del sistema',
  'Direccion principal',
  '900000000',
  'sucursal@demo.com',
  1,
  CURRENT_TIMESTAMP(6),
  CURRENT_TIMESTAMP(6),
  NULL
)
ON DUPLICATE KEY UPDATE
  id_empresa = VALUES(id_empresa),
  nombre = VALUES(nombre),
  descripcion = VALUES(descripcion),
  direccion = VALUES(direccion),
  telefono = VALUES(telefono),
  correo = VALUES(correo),
  activo = VALUES(activo),
  updated_at = CURRENT_TIMESTAMP(6);

COMMIT;
