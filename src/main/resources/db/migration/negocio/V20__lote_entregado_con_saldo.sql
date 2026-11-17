-- Nuevo estado ENTREGADO del lote (ReglasLote.estadoSegun): no hay pedidos por atender ni envíos en ruta, hay pedidos
-- entregados y todavía quedan kg sin vender. Hasta ahora esos lotes quedaban en REGISTRADO («Disponible»).
-- Deja constancia en el historial de cada lote que cambia. Sin tabla temporal (GTID), como V17.

INSERT INTO evento_lote (id_lote, tipo_evento, descripcion_evento, usuario_evento, fecha_evento)
SELECT l.id_lote, 'CORRECCION', 'Estado corregido de REGISTRADO a ENTREGADO: lo vendido ya se entregó y queda saldo',
       'sistema', CURRENT_TIMESTAMP
FROM lote l
JOIN (SELECT id_lote,
             SUM(cantidad_kg) AS kg,
             SUM(estado_pedido IN ('PENDIENTE', 'DESPACHADO')) AS por_atender,
             SUM(estado_pedido = 'ENTREGADO') AS entregados
      FROM pedido
      WHERE estado_pedido <> 'CANCELADO'
      GROUP BY id_lote) p ON p.id_lote = l.id_lote
WHERE l.estado_lote = 'REGISTRADO'
  AND p.por_atender = 0
  AND p.entregados > 0
  AND l.cantidad_kg - p.kg > 0
  AND NOT EXISTS (SELECT 1 FROM envio e WHERE e.id_lote = l.id_lote AND e.estado_envio = 'EN_RUTA');

UPDATE lote l
JOIN (SELECT id_lote,
             SUM(cantidad_kg) AS kg,
             SUM(estado_pedido IN ('PENDIENTE', 'DESPACHADO')) AS por_atender,
             SUM(estado_pedido = 'ENTREGADO') AS entregados
      FROM pedido
      WHERE estado_pedido <> 'CANCELADO'
      GROUP BY id_lote) p ON p.id_lote = l.id_lote
SET l.estado_lote = 'ENTREGADO'
WHERE l.estado_lote = 'REGISTRADO'
  AND p.por_atender = 0
  AND p.entregados > 0
  AND l.cantidad_kg - p.kg > 0
  AND NOT EXISTS (SELECT 1 FROM envio e WHERE e.id_lote = l.id_lote AND e.estado_envio = 'EN_RUTA');
