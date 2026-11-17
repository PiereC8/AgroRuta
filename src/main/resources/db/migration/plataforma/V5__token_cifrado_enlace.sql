-- RFF-09: el enlace se guarda también cifrado (AES-GCM, con una clave que no está en la base), para volver a
-- mostrarlo con su QR al abrir «Enlaces». El hash sigue siendo lo que autentica; los enlaces anteriores quedan sin
-- token cifrado y se muestran con el botón para generar uno nuevo.
ALTER TABLE enlace_envio
    ADD COLUMN token_cifrado VARCHAR(255) NULL AFTER hash_token;
