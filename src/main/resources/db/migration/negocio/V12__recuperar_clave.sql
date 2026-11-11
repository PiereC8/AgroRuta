-- Enlaces de un solo uso para restablecer la contraseña olvidada. Solo se guarda el hash del token.
CREATE TABLE recuperacion_clave (
    id_recuperacion    BIGINT       NOT NULL AUTO_INCREMENT,
    id_usuario         BIGINT       NOT NULL,
    hash_token         CHAR(64)     NOT NULL,
    fecha_creacion     TIMESTAMP(3) NOT NULL,
    fecha_vencimiento  TIMESTAMP(3) NOT NULL,
    fecha_uso          TIMESTAMP(3) NULL,
    PRIMARY KEY (id_recuperacion),
    UNIQUE KEY uk_recuperacion_token (hash_token),
    KEY ix_recuperacion_usuario (id_usuario, fecha_creacion),
    CONSTRAINT fk_recuperacion_usuario FOREIGN KEY (id_usuario) REFERENCES usuario (id_usuario)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
