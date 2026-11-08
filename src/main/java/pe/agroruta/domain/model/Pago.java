package pe.agroruta.domain.model;

import java.time.Instant;
import java.util.regex.Pattern;

/**
 * Un intento de pago de una liquidación. Guarda solo la marca y los últimos 4 dígitos de la tarjeta:
 * el número completo nunca pasa por el sistema.
 */
public record Pago(Long idPago, Long idLiquidacion, Long idPedido, Dinero monto, EstadoPago estadoPago,
                   String claveIdempotencia, String idCargoPasarela, String codigoRespuesta, String mensajeUsuario,
                   String marcaTarjeta, String ultimosCuatro, String emailPagador, Instant fechaCreacion,
                   Instant fechaActualizacion) {

    private static final Pattern FORMATO_CLAVE = Pattern.compile("^[A-Za-z0-9_-]{8,64}$");
    private static final Pattern FORMATO_EMAIL = Pattern.compile("^[^@\\s]+@[^@\\s]+\\.[^@\\s]+$");

    public Pago {
        if (idLiquidacion == null || idPedido == null || monto == null || estadoPago == null
                || fechaCreacion == null || fechaActualizacion == null) {
            throw new DominioException("El pago necesita liquidación, pedido, monto, estado y fechas");
        }
        if (ultimosCuatro != null && !ultimosCuatro.matches("\\d{4}")) {
            throw new DominioException("Solo se guardan los últimos 4 dígitos de la tarjeta");
        }
    }

    public static Pago nuevo(Liquidacion liquidacion, String emailPagador, String claveIdempotencia, Instant ahora) {
        if (claveIdempotencia == null || !FORMATO_CLAVE.matcher(claveIdempotencia).matches()) {
            throw new DominioException("La clave de la operación debe tener de 8 a 64 caracteres (letras, números, - o _)");
        }
        if (emailPagador == null || emailPagador.length() > 120 || !FORMATO_EMAIL.matcher(emailPagador).matches()) {
            throw new DominioException("El correo no es válido");
        }
        Dinero monto = liquidacion.ingresoReal();
        if (!monto.esPositivo()) {
            throw new DominioException("No hay nada que cobrar: el ingreso real de este pedido es cero");
        }
        return new Pago(null, liquidacion.idLiquidacion(), liquidacion.idPedido(), monto, EstadoPago.PENDIENTE,
                claveIdempotencia, null, null, null, null, null, emailPagador, ahora, ahora);
    }

    public Pago aplicar(ResultadoCobro resultado, Instant ahora) {
        if (resultado.estado() == ResultadoCobro.Estado.PENDIENTE) {
            boolean codigoGenerado = estadoPago == EstadoPago.PENDIENTE && resultado.idCargo() != null
                    && ResultadoCobro.CIP_GENERADO.equals(resultado.codigoRespuesta());
            return codigoGenerado
                    ? new Pago(idPago, idLiquidacion, idPedido, monto, EstadoPago.PENDIENTE, claveIdempotencia,
                            resultado.idCargo(), resultado.codigoRespuesta(), resultado.mensajePasarela(),
                            resultado.marcaTarjeta(), null, emailPagador, fechaCreacion, ahora)
                    : this;
        }
        if (estadoPago == EstadoPago.APROBADO) {
            return this;
        }
        if (estadoPago == EstadoPago.RECHAZADO && resultado.estado() == ResultadoCobro.Estado.RECHAZADO) {
            return this;
        }
        if (resultado.estado() == ResultadoCobro.Estado.APROBADO) {
            return new Pago(idPago, idLiquidacion, idPedido, monto, EstadoPago.APROBADO, claveIdempotencia,
                    resultado.idCargo(), resultado.codigoRespuesta(), "Pago aprobado", resultado.marcaTarjeta(),
                    resultado.ultimosCuatro(), emailPagador, fechaCreacion, ahora);
        }
        return new Pago(idPago, idLiquidacion, idPedido, monto, EstadoPago.RECHAZADO, claveIdempotencia,
                resultado.idCargo(), resultado.codigoRespuesta(),
                MensajesPago.paraUsuario(resultado.codigoRespuesta(), resultado.mensajePasarela()), marcaTarjeta,
                ultimosCuatro, emailPagador, fechaCreacion, ahora);
    }

    public Pago conId(Long nuevoIdPago) {
        return new Pago(nuevoIdPago, idLiquidacion, idPedido, monto, estadoPago, claveIdempotencia, idCargoPasarela,
                codigoRespuesta, mensajeUsuario, marcaTarjeta, ultimosCuatro, emailPagador, fechaCreacion,
                fechaActualizacion);
    }
}
