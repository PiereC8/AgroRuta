-- Corrige el estado guardado de los lotes con las mismas reglas que ReglasLote.estadoSegun (RFF-05 y RFF-07).
-- Hasta el 2026-11-14, registrar un pedido no cambiaba el estado del lote (pasaba a RESERVADO recién al
-- despachar), así que un lote con su pedido pendiente quedaba en REGISTRADO («Disponible») con 0 kg libres.
-- Esta migración recalcula el estado de todos los lotes y deja constancia en el historial de cada lote que cambia.
--   DESPACHADO: algún envío del lote va en ruta.
--   RESERVADO:  hay pedidos pendientes o despachados.
--   LIQUIDADO:  hay pedidos entregados y no quedan kilos disponibles.
--   REGISTRADO: en cualquier otro caso.
-- (Sin tabla temporal: con GTID activo, MySQL no la permite dentro de la transacción de la migración.)

-- 1. Historial: una línea por cada lote cuyo estado va a cambiar.
INSERT INTO evento_lote (id_lote, tipo_evento, descripcion_evento, usuario_evento, fecha_evento)
SELECT c.id_lote, 'CORRECCION',
       CONCAT('Estado corregido de ', c.estado_anterior, ' a ', c.estado_nuevo, ' según sus pedidos y envíos'),
       'sistema', CURRENT_TIMESTAMP
FROM (SELECT l.id_lote,
             l.estado_lote AS estado_anterior,
             CASE
                 WHEN COALESCE(e.en_ruta, 0) > 0 THEN 'DESPACHADO'
                 WHEN COALESCE(p.por_atender, 0) > 0 THEN 'RESERVADO'
                 WHEN COALESCE(p.entregados, 0) > 0 AND l.cantidad_kg - COALESCE(p.kg, 0) = 0 THEN 'LIQUIDADO'
                 ELSE 'REGISTRADO'
             END AS estado_nuevo
      FROM lote l
      LEFT JOIN (SELECT id_lote,
                        SUM(cantidad_kg) AS kg,
                        SUM(estado_pedido IN ('PENDIENTE', 'DESPACHADO')) AS por_atender,
                        SUM(estado_pedido = 'ENTREGADO') AS entregados
                 FROM pedido
                 WHERE estado_pedido <> 'CANCELADO'
                 GROUP BY id_lote) p ON p.id_lote = l.id_lote
      LEFT JOIN (SELECT id_lote, COUNT(*) AS en_ruta
                 FROM envio
                 WHERE estado_envio = 'EN_RUTA'
                 GROUP BY id_lote) e ON e.id_lote = l.id_lote) c
WHERE c.estado_anterior <> c.estado_nuevo;

-- 2. El estado nuevo (mismo cálculo).
UPDATE lote l
LEFT JOIN (SELECT id_lote,
                  SUM(cantidad_kg) AS kg,
                  SUM(estado_pedido IN ('PENDIENTE', 'DESPACHADO')) AS por_atender,
                  SUM(estado_pedido = 'ENTREGADO') AS entregados
           FROM pedido
           WHERE estado_pedido <> 'CANCELADO'
           GROUP BY id_lote) p ON p.id_lote = l.id_lote
LEFT JOIN (SELECT id_lote, COUNT(*) AS en_ruta
           FROM envio
           WHERE estado_envio = 'EN_RUTA'
           GROUP BY id_lote) e ON e.id_lote = l.id_lote
SET l.estado_lote = CASE
        WHEN COALESCE(e.en_ruta, 0) > 0 THEN 'DESPACHADO'
        WHEN COALESCE(p.por_atender, 0) > 0 THEN 'RESERVADO'
        WHEN COALESCE(p.entregados, 0) > 0 AND l.cantidad_kg - COALESCE(p.kg, 0) = 0 THEN 'LIQUIDADO'
        ELSE 'REGISTRADO'
    END;
