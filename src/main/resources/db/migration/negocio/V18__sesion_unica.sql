-- RFF-01: una sola sesión abierta por cuenta. Se guarda la huella (hash) de la sesión activa y su último uso;
-- sin actividad por 5 minutos, la cuenta queda libre para entrar desde otro dispositivo.
ALTER TABLE usuario
    ADD COLUMN sesion_hash CHAR(64) NULL,
    ADD COLUMN sesion_ultimo_uso TIMESTAMP(3) NULL;
