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
- Un negocio por base de datos (multi-tenant) y dos roles: Dueño y Operador.

## Tecnologías

- Java 21 y Spring Boot 3.5
- MySQL 8 con Flyway para las migraciones
- Spring Security con JWT, WebSocket (STOMP) para el seguimiento
- HTML, CSS y JavaScript sin frameworks; mapas con Leaflet y OpenStreetMap
- Pruebas con JUnit 5, Mockito y ArchUnit

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

Si no configuras las llaves de Mercado Pago y Nubefact, el sistema usa un pago y un comprobante simulados, así que se puede probar todo sin cuentas externas.

## Pruebas

```
./mvnw verify
```
