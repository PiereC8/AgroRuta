CREATE TABLE comprador (
    id_comprador        BIGINT       NOT NULL AUTO_INCREMENT,
    nombre_comprador    VARCHAR(120) NOT NULL,
    tipo_comprador      VARCHAR(20)  NOT NULL,
    documento_comprador VARCHAR(11)  NULL,
    telefono_comprador  VARCHAR(20)  NULL,
    ciudad_comprador    VARCHAR(80)  NOT NULL,
    comprador_activo    BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion      TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_comprador),
    UNIQUE KEY uk_comprador_documento (documento_comprador)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE transportista (
    id_transportista     BIGINT       NOT NULL AUTO_INCREMENT,
    nombre_transportista VARCHAR(120) NOT NULL,
    telefono_transportista VARCHAR(20) NULL,
    transportista_activo BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_transportista)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE vehiculo (
    id_vehiculo      BIGINT         NOT NULL AUTO_INCREMENT,
    id_transportista BIGINT         NOT NULL,
    placa_vehiculo   VARCHAR(10)    NOT NULL,
    capacidad_kg     DECIMAL(10, 3) NOT NULL,
    vehiculo_activo  BOOLEAN        NOT NULL DEFAULT TRUE,
    fecha_creacion   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_vehiculo),
    UNIQUE KEY uk_vehiculo_placa (placa_vehiculo),
    CONSTRAINT fk_vehiculo_transportista FOREIGN KEY (id_transportista) REFERENCES transportista (id_transportista)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE pedido (
    id_pedido          BIGINT         NOT NULL AUTO_INCREMENT,
    id_comprador       BIGINT         NOT NULL,
    id_lote            BIGINT         NOT NULL,
    id_destino_venta   BIGINT         NOT NULL,
    cantidad_kg        DECIMAL(10, 3) NOT NULL,
    precio_acordado_kg DECIMAL(10, 2) NOT NULL,
    estado_pedido      VARCHAR(20)    NOT NULL,
    usuario_registro   VARCHAR(50)    NOT NULL,
    fecha_pedido       TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_pedido),
    UNIQUE KEY uk_pedido_lote (id_lote),
    KEY ix_pedido_comprador (id_comprador),
    CONSTRAINT fk_pedido_comprador FOREIGN KEY (id_comprador) REFERENCES comprador (id_comprador),
    CONSTRAINT fk_pedido_lote FOREIGN KEY (id_lote) REFERENCES lote (id_lote),
    CONSTRAINT fk_pedido_destino FOREIGN KEY (id_destino_venta) REFERENCES destino_venta (id_destino_venta)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE envio (
    id_envio         BIGINT      NOT NULL AUTO_INCREMENT,
    id_pedido        BIGINT      NOT NULL,
    id_lote          BIGINT      NOT NULL,
    id_vehiculo      BIGINT      NOT NULL,
    id_transportista BIGINT      NOT NULL,
    estado_envio     VARCHAR(20) NOT NULL,
    fecha_programada TIMESTAMP   NOT NULL,
    fecha_salida     TIMESTAMP   NULL,
    fecha_entrega    TIMESTAMP   NULL,
    usuario_despacho VARCHAR(50) NOT NULL,
    PRIMARY KEY (id_envio),
    UNIQUE KEY uk_envio_pedido (id_pedido),
    KEY ix_envio_vehiculo_estado (id_vehiculo, estado_envio),
    CONSTRAINT fk_envio_pedido FOREIGN KEY (id_pedido) REFERENCES pedido (id_pedido),
    CONSTRAINT fk_envio_lote FOREIGN KEY (id_lote) REFERENCES lote (id_lote),
    CONSTRAINT fk_envio_vehiculo FOREIGN KEY (id_vehiculo) REFERENCES vehiculo (id_vehiculo),
    CONSTRAINT fk_envio_transportista FOREIGN KEY (id_transportista) REFERENCES transportista (id_transportista)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
