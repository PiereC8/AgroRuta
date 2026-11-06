CREATE TABLE destino_venta (
    id_destino_venta BIGINT      NOT NULL AUTO_INCREMENT,
    nombre_destino   VARCHAR(80) NOT NULL,
    ciudad_destino   VARCHAR(80) NOT NULL,
    es_local         BOOLEAN     NOT NULL DEFAULT FALSE,
    destino_activo   BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion   TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_destino_venta),
    UNIQUE KEY uk_destino_nombre (nombre_destino)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE precio_destino (
    id_destino_venta    BIGINT         NOT NULL,
    producto_lote       VARCHAR(80)    NOT NULL,
    precio_esperado_kg  DECIMAL(10, 2) NOT NULL,
    fecha_actualizacion TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_destino_venta, producto_lote),
    CONSTRAINT fk_precio_destino FOREIGN KEY (id_destino_venta) REFERENCES destino_venta (id_destino_venta)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE tarifa_flete_destino (
    id_destino_venta    BIGINT         NOT NULL,
    costo_flete_kg      DECIMAL(10, 2) NOT NULL,
    fecha_actualizacion TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id_destino_venta),
    CONSTRAINT fk_tarifa_destino FOREIGN KEY (id_destino_venta) REFERENCES destino_venta (id_destino_venta)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
