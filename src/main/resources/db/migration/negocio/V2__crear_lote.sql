CREATE TABLE lote (
    id_lote          BIGINT         NOT NULL AUTO_INCREMENT,
    producto_lote    VARCHAR(80)    NOT NULL,
    cantidad_kg      DECIMAL(10, 3) NOT NULL,
    fecha_cosecha    DATE           NOT NULL,
    chacra_origen    VARCHAR(120)   NOT NULL,
    estado_lote      VARCHAR(20)    NOT NULL,
    usuario_registro VARCHAR(50)    NOT NULL,
    fecha_registro   TIMESTAMP      NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_lote),
    KEY ix_lote_fecha_cosecha (fecha_cosecha),
    KEY ix_lote_estado (estado_lote)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;

CREATE TABLE evento_lote (
    id_evento_lote     BIGINT       NOT NULL AUTO_INCREMENT,
    id_lote            BIGINT       NOT NULL,
    tipo_evento        VARCHAR(20)  NOT NULL,
    descripcion_evento VARCHAR(255) NOT NULL,
    usuario_evento     VARCHAR(50)  NOT NULL,
    fecha_evento       TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_evento_lote),
    KEY ix_evento_lote_lote (id_lote, fecha_evento),
    CONSTRAINT fk_evento_lote_lote FOREIGN KEY (id_lote) REFERENCES lote (id_lote)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
