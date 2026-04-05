ALTER TABLE sucursal
  ADD COLUMN ciudad VARCHAR(100) NULL AFTER nombre,
  ADD COLUMN direccion VARCHAR(255) NULL AFTER ciudad;

UPDATE sucursal s
JOIN empresa e ON e.id_empresa = s.id_empresa
SET
  s.ciudad = COALESCE(NULLIF(TRIM(s.ciudad), ''), NULLIF(TRIM(e.provincia), ''), NULLIF(TRIM(e.departamento), '')),
  s.direccion = COALESCE(NULLIF(TRIM(s.direccion), ''), NULLIF(TRIM(e.direccion), ''))
WHERE s.ciudad IS NULL OR TRIM(s.ciudad) = '' OR s.direccion IS NULL OR TRIM(s.direccion) = '';

ALTER TABLE sucursal
  DROP COLUMN descripcion;
