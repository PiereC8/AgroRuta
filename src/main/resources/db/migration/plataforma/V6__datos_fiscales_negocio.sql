-- Datos con los que cada negocio emite sus boletas y facturas. Si faltan, se usan las variables EMISOR_* (demo).
ALTER TABLE negocio
    ADD COLUMN ruc_emisor       VARCHAR(11)  NULL,
    ADD COLUMN razon_social     VARCHAR(150) NULL,
    ADD COLUMN domicilio_fiscal VARCHAR(200) NULL;
