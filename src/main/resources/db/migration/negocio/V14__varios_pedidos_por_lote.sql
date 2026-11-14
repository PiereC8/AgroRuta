-- Un lote puede repartirse entre varios pedidos (mientras no se pase de sus kg), y cada envío tiene su liquidación.
-- Las reglas de stock se controlan en el servicio, con la fila del lote bloqueada, no con una restricción única.
ALTER TABLE pedido
    DROP INDEX uk_pedido_lote_activo,
    DROP COLUMN lote_activo;

-- La llave foránea de liquidacion necesita un índice por id_lote antes de quitar el único.
ALTER TABLE liquidacion ADD KEY ix_liquidacion_lote (id_lote);
ALTER TABLE liquidacion DROP INDEX uk_liquidacion_lote;
