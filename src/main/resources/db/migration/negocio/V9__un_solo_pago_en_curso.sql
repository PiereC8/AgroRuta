-- Una liquidación solo puede tener un pago en curso (pendiente) o aprobado a la vez. El pago se guarda como
-- pendiente antes de llamar a la pasarela, así que un segundo intento simultáneo choca aquí y nunca llega a cobrar.
-- Los pagos rechazados no cuentan: después de un rechazo se puede volver a intentar.
ALTER TABLE pago
    ADD COLUMN liquidacion_en_curso BIGINT
        AS (IF(estado_pago IN ('PENDIENTE', 'APROBADO'), id_liquidacion, NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_pago_en_curso (liquidacion_en_curso);
