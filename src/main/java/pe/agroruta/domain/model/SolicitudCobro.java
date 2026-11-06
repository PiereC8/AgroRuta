package pe.agroruta.domain.model;

import java.util.Map;

/** Lo que se le pide a la pasarela. El token de la tarjeta lo creó la pasarela en el navegador: el sistema nunca ve la tarjeta. */
public record SolicitudCobro(Dinero monto, String email, String tokenTarjeta, String claveIdempotencia,
                             String descripcion, Map<String, String> metadatos) {
}
