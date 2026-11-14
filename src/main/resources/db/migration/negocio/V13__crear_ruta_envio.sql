CREATE TABLE ruta_envio (
    id_envio         BIGINT       NOT NULL,
    puntos           MEDIUMTEXT   NOT NULL,
    distancia_metros DOUBLE       NOT NULL,
    duracion_segundos BIGINT      NOT NULL,
    por_carretera    BOOLEAN      NOT NULL,
    fecha_calculo    TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id_envio),
    CONSTRAINT fk_ruta_envio FOREIGN KEY (id_envio) REFERENCES envio (id_envio)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
