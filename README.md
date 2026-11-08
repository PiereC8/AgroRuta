# AgroRuta

Sistema web para negocios agrícolas que despachan su cosecha a distintos mercados. Permite registrar lotes, armar pedidos, despachar envíos con seguimiento en el mapa, calcular la ganancia real después de flete, merma y rechazo, y cobrar al comprador con comprobante electrónico.

Proyecto del curso Programación III (UPT).

## Qué hace

- Registro de lotes, compradores, destinos, transportistas y vehículos.
- Cotización de a qué destino conviene enviar cada lote (precio menos flete).
- Pedidos y envíos: despacho, salida en ruta y entrega con cantidad recibida, merma y rechazo.
- Liquidación automática de cada entrega (ingreso real, flete y ganancia real).
- Seguimiento en vivo: el conductor comparte su ubicación desde el celular y el comprador ve el mapa con un enlace.
- Cobro con tarjeta o PayPal en modo de pruebas, y boleta o factura electrónica.
- Panel de inicio con ganancia del mes, envíos en curso, merma y cobros pendientes.
- Código QR en los enlaces del conductor y del comprador, para abrirlos desde el celular sin escribir nada.
- Reportes del Dueño: ganancia, comparación con el período anterior (con el motivo del cambio), gráfico, rankings por destino, producto y comprador, filtros por período, producto y destino, y descarga en CSV o PDF.
- Equipo y avisos por correo: el Dueño pone el correo de cada persona y recibe avisos de pérdidas, merma alta, pagos, comprobantes con problemas, cobros vencidos y un resumen semanal; el Operador recibe las alertas de los envíos.
- Un negocio por base de datos (multi-tenant) y dos roles: Dueño y Operador.

## Tecnologías

- Java 21 y Spring Boot 3.5
- MySQL 8 con Flyway para las migraciones
- Spring Security con JWT, WebSocket (STOMP) para el seguimiento
- HTML, CSS y JavaScript sin frameworks; mapas con Leaflet y OpenStreetMap
- Pruebas con JUnit 5, Mockito y ArchUnit
- OpenPDF para los reportes en PDF y Spring Mail para los avisos

## Estructura

El código sigue una arquitectura hexagonal:

```
src/main/java/pe/agroruta
├── domain          modelos y puertos (Java puro)
├── application     casos de uso
└── infrastructure  controladores web, base de datos, pasarela de pago, seguridad
```

Las pantallas están en `src/main/resources/static` y las migraciones en `src/main/resources/db/migration`.

## Cómo ejecutarlo

Necesitas Java 21 y una base de datos MySQL 8.

1. Copia `.env.example` a `.env` y completa los datos de la base de datos, el secreto del JWT y el usuario administrador de la plataforma.
2. Inicia la aplicación:

   ```
   ./mvnw spring-boot:run
   ```

3. Abre `http://localhost:8080/login.html`.

Si no configuras las llaves de Mercado Pago, el sistema usa un pago simulado, y los comprobantes también se simulan, así que se puede probar todo sin cuentas externas.

### Comprobantes en el ambiente beta de SUNAT

Con `COMPROBANTES_PROVEEDOR=facturaciondirecta` en el `.env`, cada boleta o factura se envía al ambiente beta de SUNAT mediante la API de facturaciondirecta.com. La API arma el XML UBL 2.1, lo firma, lo envía y devuelve la constancia (CDR), el XML y un PDF. La beta no tiene validez tributaria y no pide RUC propio ni certificado digital; se puede usar el RUC de prueba `20000000001` (los datos del emisor van en las variables `EMISOR_*`). El producto se declara exonerado del IGV, porque se trata de productos agrícolas en estado natural.

El PDF del comprobante lo arma AgroRuta (botón «Descargar PDF» en Pagos y en la página de pago del comprador), con los mismos datos que se enviaron a SUNAT; no se usa el PDF del proveedor porque suma el IGV a la línea exonerada y lleva la marca de otra empresa. Solo se descarga si SUNAT aceptó el comprobante.

Limitaciones: el proveedor deja el XML en un enlace abierto, así que solo deben usarse datos de prueba. Para producción harían falta un RUC, un usuario SOL secundario, un certificado digital y un acuerdo con el proveedor.

### Correo de los avisos

Los avisos salen por SMTP. Con Gmail hace falta una contraseña de aplicación (Cuenta de Google, Seguridad, Verificación en dos pasos, Contraseñas de aplicaciones). En el `.env`:

```
CORREO_USUARIO=tu-cuenta@gmail.com
CORREO_CLAVE_APLICACION=la-contraseña-de-aplicación
```

Si se dejan vacíos, los correos se simulan: no salen, pero quedan en el historial de la pantalla Equipo. Desde esa pantalla también se puede enviar un correo de prueba.

## Pruebas

```
./mvnw verify
```

## Despliegue en el VPS

La carpeta `despliegue/` tiene lo necesario para un VPS Ubuntu, sin contenedores: el servicio systemd (reinicia la aplicación si se cae), la configuración de Nginx (proxy, WebSocket y página de mantenimiento) y dos scripts.

1. En el VPS, como root, una sola vez: `bash instalar.sh tu-dominio`. Instala Java 21, MySQL 8 y Nginx, crea la base y su usuario (bases con sufijo `_prod`), genera las claves en `/opt/agroruta/.env` y activa el firewall. La clave del administrador queda en `/root/agroruta-admin.txt`.
2. Desde la PC, para cada versión nueva: `bash despliegue/desplegar.sh root@IP`. Compila, sube el `.jar` y reinicia el servicio.
3. HTTPS: `certbot --nginx -d tu-dominio --redirect` (el certificado se renueva solo).

