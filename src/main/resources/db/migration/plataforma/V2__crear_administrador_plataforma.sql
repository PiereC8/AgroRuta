CREATE TABLE administrador_plataforma (
    id_usuario                BIGINT       NOT NULL AUTO_INCREMENT,
    nombre_usuario            VARCHAR(50)  NOT NULL,
    hash_clave                VARCHAR(100) NOT NULL,
    rol_usuario               VARCHAR(30)  NOT NULL,
    intentos_fallidos_sesion  INT          NOT NULL DEFAULT 0,
    fecha_bloqueo_hasta       TIMESTAMP    NULL,
    usuario_activo            BOOLEAN      NOT NULL DEFAULT TRUE,
    fecha_creacion            TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id_usuario),
    UNIQUE KEY uk_administrador_nombre (nombre_usuario)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
