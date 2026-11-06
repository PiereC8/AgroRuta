CREATE TABLE usuario (
    id                BIGINT       NOT NULL AUTO_INCREMENT,
    nombre_usuario    VARCHAR(50)  NOT NULL,
    clave_hash        VARCHAR(100) NOT NULL,
    rol               VARCHAR(20)  NOT NULL,
    intentos_fallidos INT          NOT NULL DEFAULT 0,
    bloqueado_hasta   TIMESTAMP    NULL,
    activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    creado_en         TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_usuario_nombre (nombre_usuario)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
