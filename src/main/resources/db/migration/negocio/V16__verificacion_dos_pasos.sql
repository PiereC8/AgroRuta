-- Verificación en dos pasos del Dueño: código de 6 dígitos enviado a su correo al iniciar sesión.
-- Solo se guardan hashes: el del desafío (lo tiene el navegador) y el del código unido al desafío.
CREATE TABLE verificacion_sesion (
    id_verificacion    BIGINT       NOT NULL AUTO_INCREMENT,
    id_usuario         BIGINT       NOT NULL,
    hash_desafio       CHAR(64)     NOT NULL,
    hash_codigo        CHAR(64)     NOT NULL,
    fecha_creacion     TIMESTAMP(3) NOT NULL,
    fecha_vencimiento  TIMESTAMP(3) NOT NULL,
    ultimo_envio       TIMESTAMP(3) NOT NULL,
    envios             INT          NOT NULL DEFAULT 1,
    intentos_fallidos  INT          NOT NULL DEFAULT 0,
    fecha_uso          TIMESTAMP(3) NULL,
    PRIMARY KEY (id_verificacion),
    UNIQUE KEY uk_verificacion_desafio (hash_desafio),
    KEY ix_verificacion_usuario (id_usuario, fecha_creacion),
    CONSTRAINT fk_verificacion_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
