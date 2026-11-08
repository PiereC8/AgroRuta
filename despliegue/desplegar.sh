#!/usr/bin/env bash
# Compila AgroRuta, sube el .jar al VPS y reinicia el servicio. Se ejecuta desde la PC, en la raíz del proyecto:
#   bash despliegue/desplegar.sh root@IP_DEL_VPS
# Usa la llave SSH ~/.ssh/agroruta_vps.
set -euo pipefail

DESTINO="${1:?Uso: bash despliegue/desplegar.sh usuario@ip}"
LLAVE="$HOME/.ssh/agroruta_vps"
SSH=(ssh -i "$LLAVE" -o StrictHostKeyChecking=accept-new "$DESTINO")

./mvnw -q -DskipTests package
JAR="$(ls target/agroruta-*.jar | grep -v plain | head -1)"

scp -i "$LLAVE" -o StrictHostKeyChecking=accept-new "$JAR" "$DESTINO:/tmp/agroruta.jar"
"${SSH[@]}" 'install -o agroruta -g agroruta -m 640 /tmp/agroruta.jar /opt/agroruta/agroruta.jar \
    && rm /tmp/agroruta.jar && systemctl restart agroruta && systemctl --no-pager --lines=0 status agroruta'
