CREATE TABLE negocio (
    id_negocio            BIGINT       NOT NULL AUTO_INCREMENT,
    identificador_negocio VARCHAR(31)  NOT NULL,
    nombre_negocio        VARCHAR(120) NOT NULL,
    plan_suscripcion      VARCHAR(20)  NOT NULL,
    negocio_activo        BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_negocio),
    UNIQUE KEY uk_negocio_identificador (identificador_negocio)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
