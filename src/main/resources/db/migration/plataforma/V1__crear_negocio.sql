CREATE TABLE negocio (
    id        BIGINT       NOT NULL AUTO_INCREMENT,
    slug      VARCHAR(31)  NOT NULL,
    nombre    VARCHAR(120) NOT NULL,
    plan      VARCHAR(20)  NOT NULL,
    activo    BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_negocio_slug (slug)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
