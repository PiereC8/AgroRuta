-- Los comprobantes que emitió el simulador quedaron como ACEPTADO, pero nunca se enviaron a SUNAT.
-- Se reconocen por el aviso fijo que deja el simulador.
UPDATE comprobante
SET estado_comprobante = 'SIMULADO'
WHERE estado_comprobante = 'ACEPTADO'
  AND mensaje_sunat = 'Comprobante de prueba del simulador: no tiene validez ante SUNAT';
