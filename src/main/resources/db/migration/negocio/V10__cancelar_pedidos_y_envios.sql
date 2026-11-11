-- Un pedido o un envío cancelado se conserva como historial, pero deja de ocupar su lote o su pedido:
-- la regla pasa de "un pedido por lote" a "un pedido activo por lote" (y lo mismo con los envíos).
-- Primero se crea el índice normal que necesita la llave foránea y recién después se quita el único.
ALTER TABLE pedido
    ADD COLUMN lote_activo BIGINT AS (IF(estado_pedido <> 'CANCELADO', id_lote, NULL)) VIRTUAL,
    ADD KEY ix_pedido_lote (id_lote),
    ADD UNIQUE KEY uk_pedido_lote_activo (lote_activo);
ALTER TABLE pedido DROP INDEX uk_pedido_lote;

ALTER TABLE envio
    ADD COLUMN pedido_activo BIGINT AS (IF(estado_envio <> 'CANCELADO', id_pedido, NULL)) VIRTUAL,
    ADD KEY ix_envio_pedido (id_pedido),
    ADD UNIQUE KEY uk_envio_pedido_activo (pedido_activo);
ALTER TABLE envio DROP INDEX uk_envio_pedido;
