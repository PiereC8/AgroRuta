#!/usr/bin/env bash
# Prepara un VPS Ubuntu para AgroRuta: Java 21, MySQL 8, Nginx, el servicio systemd y el firewall.
# Se ejecuta una vez, como root, desde la carpeta que tiene estos archivos:  bash instalar.sh agroruta.duckdns.org
# Las bases de producción terminan en _prod y su usuario solo puede tocar esas, para no mezclarse con las _dev.
# Genera las claves (base de datos, JWT y administrador) sin mostrarlas; la del administrador queda en
# /root/agroruta-admin.txt, que solo root puede leer.
set -euo pipefail

DOMINIO="${1:?Uso: bash instalar.sh DOMINIO}"
CARPETA="$(cd "$(dirname "$0")" && pwd)"

echo "== Paquetes"
export DEBIAN_FRONTEND=noninteractive
apt-get update -q
apt-get install -y -q openjdk-21-jre-headless mysql-server nginx certbot python3-certbot-nginx ufw openssl

echo "== Usuario y carpetas"
id agroruta >/dev/null 2>&1 || useradd --system --home-dir /opt/agroruta --shell /usr/sbin/nologin agroruta
mkdir -p /opt/agroruta /var/www/agroruta
cp "$CARPETA/mantenimiento.html" /var/www/agroruta/mantenimiento.html

echo "== Base de datos y configuración"
if [ ! -f /opt/agroruta/.env ]; then
    CLAVE_BD="$(openssl rand -hex 24)"
    SECRETO_JWT="$(openssl rand -hex 32)"
    CLAVE_ADMIN="$(openssl rand -hex 8)"
    mysql <<SQL
CREATE USER IF NOT EXISTS 'agroruta'@'localhost' IDENTIFIED BY '${CLAVE_BD}';
ALTER USER 'agroruta'@'localhost' IDENTIFIED BY '${CLAVE_BD}';
GRANT ALL PRIVILEGES ON \`agroruta\\_%\\_prod\`.* TO 'agroruta'@'localhost';
FLUSH PRIVILEGES;
SQL
    cat > /opt/agroruta/.env <<ENV
DB_PLATAFORMA_URL=jdbc:mysql://localhost:3306/agroruta_plataforma_prod
DB_USUARIO=agroruta
DB_CLAVE=${CLAVE_BD}
BD_SUFIJO=_prod
JWT_SECRETO=${SECRETO_JWT}
ADMIN_PLATAFORMA_USUARIO=admin
ADMIN_PLATAFORMA_CLAVE=${CLAVE_ADMIN}
MERCADOPAGO_LLAVE_PUBLICA=
MERCADOPAGO_TOKEN_ACCESO=
COMPROBANTES_PROVEEDOR=simulado
COMPROBANTES_SERIE_BOLETA=B001
COMPROBANTES_SERIE_FACTURA=F001
CORREO_USUARIO=
CORREO_CLAVE_APLICACION=
ENV
    umask 077
    printf 'Administrador de la plataforma\nUsuario: admin\nClave: %s\n' "${CLAVE_ADMIN}" > /root/agroruta-admin.txt
    echo "Claves generadas. La del administrador está en /root/agroruta-admin.txt"
else
    echo "/opt/agroruta/.env ya existe: no se toca."
fi
chown -R agroruta:agroruta /opt/agroruta
chmod 600 /opt/agroruta/.env

echo "== Servicio"
cp "$CARPETA/agroruta.service" /etc/systemd/system/agroruta.service
systemctl daemon-reload
systemctl enable agroruta >/dev/null

echo "== Nginx"
sed "s/DOMINIO/${DOMINIO}/g" "$CARPETA/nginx-agroruta.conf" > /etc/nginx/sites-available/agroruta
ln -sf /etc/nginx/sites-available/agroruta /etc/nginx/sites-enabled/agroruta
rm -f /etc/nginx/sites-enabled/default
nginx -t
systemctl reload nginx

echo "== Firewall (SSH, HTTP y HTTPS)"
ufw allow OpenSSH >/dev/null
ufw allow 'Nginx Full' >/dev/null
ufw --force enable >/dev/null

echo "Listo. Falta copiar /opt/agroruta/agroruta.jar y ejecutar: systemctl start agroruta"
