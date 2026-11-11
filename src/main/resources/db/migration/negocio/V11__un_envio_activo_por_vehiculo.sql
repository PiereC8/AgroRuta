-- Un vehículo solo puede tener un envío programado o en ruta a la vez. La revisión del servicio ya lo pedía, pero
-- dos despachos simultáneos podían pasarla; ahora la base lo impide.
ALTER TABLE envio
    ADD COLUMN vehiculo_en_uso BIGINT
        AS (IF(estado_envio IN ('PROGRAMADO', 'EN_RUTA'), id_vehiculo, NULL)) VIRTUAL,
    ADD UNIQUE KEY uk_envio_vehiculo_en_uso (vehiculo_en_uso);
