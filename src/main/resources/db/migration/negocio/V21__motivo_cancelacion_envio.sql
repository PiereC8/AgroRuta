-- Por qué se canceló un envío. Obligatorio (en la aplicación) si el envío ya iba en ruta.
ALTER TABLE envio ADD COLUMN motivo_cancelacion VARCHAR(255) NULL;
