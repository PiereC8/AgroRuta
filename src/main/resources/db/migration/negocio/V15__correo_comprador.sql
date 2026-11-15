-- Correo opcional del comprador: al despachar se le manda solo el enlace para seguir su pedido.
ALTER TABLE comprador
    ADD COLUMN correo_comprador VARCHAR(120) NULL AFTER telefono_comprador;
