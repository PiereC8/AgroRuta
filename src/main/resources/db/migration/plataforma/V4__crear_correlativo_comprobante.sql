CREATE TABLE correlativo_comprobante (
    serie         CHAR(4) NOT NULL,
    ultimo_numero INT     NOT NULL,
    PRIMARY KEY (serie)
) ENGINE = InnoDB DEFAULT CHARSET = utf8mb4;
