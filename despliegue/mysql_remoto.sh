#!/usr/bin/env bash
# Deja que el backend en Render use el MySQL de este VPS, sin abrirlo a todo internet.
# Uso (como root):  bash mysql_remoto.sh IP1 IP2 ...   (las IP de salida que Render muestra en el servicio).
# - MySQL escucha en todas las interfaces, pero el firewall solo deja entrar a esas IP por el puerto 3306.
# - Se crea el usuario agroruta_remoto, que solo puede tocar las bases _prod y exige conexión cifrada (TLS).
# - Su clave se genera aquí y queda en /root/agroruta-render-bd.txt (solo root la puede leer); no se muestra.
# Volver a correrlo con otras IP agrega esas IP; la clave no cambia si el archivo ya existe.
set -euo pipefail

[ "$#" -ge 1 ] || { echo "Uso: bash mysql_remoto.sh IP1 [IP2 ...]"; exit 1; }

echo "== MySQL escuchando fuera de localhost"
cat > /etc/mysql/mysql.conf.d/zz-agroruta-remoto.cnf <<'CNF'
[mysqld]
bind-address = 0.0.0.0
mysqlx-bind-address = 127.0.0.1
CNF

echo "== Usuario remoto"
ARCHIVO=/root/agroruta-render-bd.txt
if [ -f "$ARCHIVO" ]; then
    CLAVE="$(sed -n 's/^Clave: //p' "$ARCHIVO")"
else
    CLAVE="$(openssl rand -hex 24)"
    umask 077
    printf 'Usuario MySQL para Render\nUsuario: agroruta_remoto\nClave: %s\n' "$CLAVE" > "$ARCHIVO"
fi
mysql <<SQL
CREATE USER IF NOT EXISTS 'agroruta_remoto'@'%' IDENTIFIED BY '${CLAVE}' REQUIRE SSL;
ALTER USER 'agroruta_remoto'@'%' IDENTIFIED BY '${CLAVE}' REQUIRE SSL;
GRANT ALL PRIVILEGES ON \`agroruta\\_%\\_prod\`.* TO 'agroruta_remoto'@'%';
FLUSH PRIVILEGES;
SQL

echo "== Firewall: solo las IP de Render"
for ip in "$@"; do
    ufw allow from "$ip" to any port 3306 proto tcp comment 'MySQL para Render'
done

systemctl restart mysql
echo "Listo. La clave del usuario agroruta_remoto está en $ARCHIVO"
