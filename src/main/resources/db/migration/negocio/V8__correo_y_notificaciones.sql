ALTER TABLE usuario
    ADD COLUMN correo_usuario VARCHAR(120) NULL,
    ADD COLUMN avisos_activos BOOLEAN NOT NULL DEFAULT TRUE;

-- Bandeja de salida de los avisos por correo. La clave única impide repetir un mismo aviso al mismo correo.
CREATE TABLE notificacion (
    id_notificacion   BIGINT        NOT NULL AUTO_INCREMENT,
    clave_unica       VARCHAR(190)  NOT NULL,
    tipo_aviso        VARCHAR(40)   NOT NULL,
    correo_destino    VARCHAR(120)  NOT NULL,
    asunto            VARCHAR(200)  NOT NULL,
    cuerpo            TEXT          NOT NULL,
    estado            VARCHAR(12)   NOT NULL,
    intentos          INT           NOT NULL DEFAULT 0,
    ultimo_error      VARCHAR(300)  NULL,
    fecha_creacion    TIMESTAMP     NOT NULL,
    proximo_intento   TIMESTAMP     NOT NULL,
    fecha_envio       TIMESTAMP     NULL,
    PRIMARY KEY (id_notificacion),
    UNIQUE KEY uk_notificacion_clave (clave_unica),
    KEY ix_notificacion_pendientes (estado, proximo_intento)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
