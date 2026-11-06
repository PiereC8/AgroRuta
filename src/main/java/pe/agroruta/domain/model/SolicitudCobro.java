package pe.agroruta.domain.model;

import java.util.Map;

/**
 * Lo que se le pide a la pasarela. El token de la tarjeta lo creó la pasarela en el navegador: el sistema nunca ve la
 * tarjeta. {@code metodoPago} (por ejemplo "visa") y {@code emisor} los entrega el formulario de la pasarela; el
 * simulador no los necesita.
 */
public record SolicitudCobro(Dinero monto, String email, String tokenTarjeta, String claveIdempotencia,
                             String descripcion, Map<String, String> metadatos, String metodoPago, String emisor) {

    public SolicitudCobro(Dinero monto, String email, String tokenTarjeta, String claveIdempotencia,
                          String descripcion, Map<String, String> metadatos) {
        this(monto, email, tokenTarjeta, claveIdempotencia, descripcion, metadatos, null, null);
    }
}
