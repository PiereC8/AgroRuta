package pe.agroruta.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;

/** Boleta o factura de un pago. Mientras esté PENDIENTE se reintenta sola cada 5 minutos, sin frenar el cobro. */
public record Comprobante(Long idComprobante, Long idPago, TipoComprobante tipoComprobante, String serie,
                          int numero, EstadoComprobante estadoComprobante, String enlacePdf, String enlaceXml,
                          String mensajeSunat, int intentos, Instant proximoIntento, LocalDate fechaEmision,
                          Instant fechaCreacion) {

    public static final Duration ESPERA_ENTRE_INTENTOS = Duration.ofMinutes(5);
    public static final int MAXIMO_INTENTOS_AUTOMATICOS = 50;

    public static Comprobante nuevo(Long idPago, TipoComprobante tipo, String serie, int numero,
                                    LocalDate fechaEmision, Instant ahora) {
        return new Comprobante(null, idPago, tipo, serie, numero, EstadoComprobante.PENDIENTE, null, null, null, 0,
                ahora, fechaEmision, ahora);
    }

    public String numeroCompleto() {
        return serie + "-" + numero;
    }

    public Comprobante conResultado(ResultadoEmision resultado, Instant ahora) {
        if (resultado.estado() == EstadoComprobante.PENDIENTE) {
            return reintentar(resultado.mensajeSunat(), ahora);
        }
        return new Comprobante(idComprobante, idPago, tipoComprobante, serie, numero, resultado.estado(),
                resultado.enlacePdf(), resultado.enlaceXml(), resultado.mensajeSunat(), intentos + 1, null,
                fechaEmision, fechaCreacion);
    }

    /** El proveedor no respondió: queda pendiente y se vuelve a intentar más tarde. */
    public Comprobante fallido(String motivo, Instant ahora) {
        return reintentar(motivo, ahora);
    }

    public boolean esPendiente() {
        return estadoComprobante == EstadoComprobante.PENDIENTE;
    }

    private Comprobante reintentar(String mensaje, Instant ahora) {
        int nuevosIntentos = intentos + 1;
        Instant proximo = nuevosIntentos >= MAXIMO_INTENTOS_AUTOMATICOS ? null : ahora.plus(ESPERA_ENTRE_INTENTOS);
        return new Comprobante(idComprobante, idPago, tipoComprobante, serie, numero, EstadoComprobante.PENDIENTE,
                enlacePdf, enlaceXml, mensaje, nuevosIntentos, proximo, fechaEmision, fechaCreacion);
    }

    public Comprobante conId(Long nuevoIdComprobante) {
        return new Comprobante(nuevoIdComprobante, idPago, tipoComprobante, serie, numero, estadoComprobante,
                enlacePdf, enlaceXml, mensajeSunat, intentos, proximoIntento, fechaEmision, fechaCreacion);
    }
}
