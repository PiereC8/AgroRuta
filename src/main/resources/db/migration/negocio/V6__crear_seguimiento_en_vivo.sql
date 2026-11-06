CREATE TABLE coordenada_destino (
    id_destino_venta    BIGINT        NOT NULL,
    latitud             DECIMAL(9, 6) NOT NULL,
    longitud            DECIMAL(9, 6) NOT NULL,
    fecha_actualizacion TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_destino_venta),
    CONSTRAINT fk_coordenada_destino FOREIGN KEY (id_destino_venta) REFERENCES destino_venta (id_destino_venta)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE seguimiento_envio (
    id_envio             BIGINT       NOT NULL,
    fecha_consentimiento TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id_envio),
    CONSTRAINT fk_seguimiento_envio FOREIGN KEY (id_envio) REFERENCES envio (id_envio)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE posicion_envio (
    id_posicion      BIGINT        NOT NULL AUTO_INCREMENT,
    id_envio         BIGINT        NOT NULL,
    latitud          DECIMAL(9, 6) NOT NULL,
    longitud         DECIMAL(9, 6) NOT NULL,
    precision_metros DECIMAL(9, 1) NULL,
    fecha_posicion   TIMESTAMP(3)  NOT NULL,
    fecha_recepcion  TIMESTAMP(3)  NOT NULL,
    PRIMARY KEY (id_posicion),
    UNIQUE KEY uk_posicion_envio_fecha (id_envio, fecha_posicion),
    CONSTRAINT fk_posicion_envio FOREIGN KEY (id_envio) REFERENCES envio (id_envio)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE alerta_envio (
    id_alerta          BIGINT       NOT NULL AUTO_INCREMENT,
    id_envio           BIGINT       NOT NULL,
    tipo_alerta        VARCHAR(20)  NOT NULL,
    descripcion_alerta VARCHAR(255) NOT NULL,
    fecha_alerta       TIMESTAMP(3) NOT NULL,
    PRIMARY KEY (id_alerta),
    KEY ix_alerta_envio_tipo (id_envio, tipo_alerta, fecha_alerta),
    CONSTRAINT fk_alerta_envio FOREIGN KEY (id_envio) REFERENCES envio (id_envio)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
