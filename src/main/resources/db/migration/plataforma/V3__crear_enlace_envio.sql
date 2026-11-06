CREATE TABLE enlace_envio (
    id_enlace             BIGINT      NOT NULL AUTO_INCREMENT,
    hash_token            CHAR(64)    NOT NULL,
    tipo_enlace           VARCHAR(20) NOT NULL,
    identificador_negocio VARCHAR(31) NOT NULL,
    id_envio              BIGINT      NOT NULL,
    fecha_expiracion      TIMESTAMP   NOT NULL,
    enlace_activo         BOOLEAN     NOT NULL DEFAULT TRUE,
    fecha_creacion        TIMESTAMP   NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_enlace),
    UNIQUE KEY uk_enlace_hash (hash_token),
    KEY ix_enlace_envio (identificador_negocio, id_envio, tipo_enlace)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
