package pe.agroruta.domain.model;

import java.util.Map;

public record SolicitudCobro(Dinero monto, String email, String tokenTarjeta, String claveIdempotencia,
                             String descripcion, Map<String, String> metadatos, String metodoPago, String emisor) {

    public SolicitudCobro(Dinero monto, String email, String tokenTarjeta, String claveIdempotencia,
                          String descripcion, Map<String, String> metadatos) {
        this(monto, email, tokenTarjeta, claveIdempotencia, descripcion, metadatos, null, null);
    }
}
