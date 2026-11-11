package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.port.in.ConciliarPagosUseCase;
import pe.agroruta.domain.port.in.EmitirComprobanteUseCase;
import pe.agroruta.domain.port.out.PagoPort;
import pe.agroruta.domain.port.out.PagoRepositoryPort;

/**
 * Un pago queda PENDIENTE si la pasarela no respondió al cobrar o si es un código de pago (CIP) que se paga después.
 * Sin esta tarea quedaba así para siempre: el comprador no podía reintentar y el negocio no podía resolverlo.
 * <ul>
 *   <li>Si se conoce el cargo, se le pregunta a la pasarela y se aplica lo que diga.</li>
 *   <li>Un CIP que pasó su plazo de 48 h sin pagarse se vence (rechazado).</li>
 *   <li>Un pago del que la pasarela nunca devolvió el cargo se libera tras 2 h, para poder pagar de nuevo. Si la
 *       pasarela lo confirma después, su notificación lo aprueba igual.</li>
 * </ul>
 */
public class ConciliarPagosService implements ConciliarPagosUseCase {

    private static final Logger REGISTRO = LoggerFactory.getLogger(ConciliarPagosService.class);

    static final Duration ESPERA_ANTES_DE_REVISAR = Duration.ofMinutes(10);
    static final Duration VIGENCIA_DEL_CODIGO = Duration.ofHours(48);
    static final Duration ESPERA_SIN_CARGO = Duration.ofHours(2);
    static final String CODIGO_VENCIDO = "cip_vencido";
    static final String SIN_CONFIRMAR = "sin_confirmar";
    private static final int POR_VUELTA = 50;

    private final PagoPort pasarela;
    private final PagoRepositoryPort pagos;
    private final EmitirComprobanteUseCase emitirComprobante;
    private final Clock reloj;

    public ConciliarPagosService(PagoPort pasarela, PagoRepositoryPort pagos, EmitirComprobanteUseCase emitirComprobante,
                                 Clock reloj) {
        this.pasarela = pasarela;
        this.pagos = pagos;
        this.emitirComprobante = emitirComprobante;
        this.reloj = reloj;
    }

    @Override
    public int conciliar() {
        Instant ahora = reloj.instant();
        int cambiados = 0;
        for (Pago pago : pagos.pendientesCreadosAntesDe(ahora.minus(ESPERA_ANTES_DE_REVISAR), POR_VUELTA)) {
            try {
                if (resolver(pago, ahora)) {
                    cambiados++;
                }
            } catch (RuntimeException e) {
                REGISTRO.warn("No se pudo conciliar el pago {}: {}", pago.idPago(), e.getMessage());
            }
        }
        return cambiados;
    }

    private boolean resolver(Pago pago, Instant ahora) {
        Duration edad = Duration.between(pago.fechaCreacion(), ahora);
        if (pago.idCargoPasarela() == null) {
            if (edad.compareTo(ESPERA_SIN_CARGO) < 0) {
                return false;
            }
            return aplicar(pago, ResultadoCobro.rechazado(null, SIN_CONFIRMAR,
                    "No se pudo confirmar el pago con la pasarela; puedes intentar de nuevo", Map.of()), ahora);
        }
        Optional<ResultadoCobro> cargo = pasarela.consultarCargo(pago.idCargoPasarela());
        if (cargo.isPresent() && cargo.get().estado() != ResultadoCobro.Estado.PENDIENTE) {
            return aplicar(pago, cargo.get(), ahora);
        }
        if (ResultadoCobro.CIP_GENERADO.equals(pago.codigoRespuesta()) && edad.compareTo(VIGENCIA_DEL_CODIGO) > 0) {
            return aplicar(pago, ResultadoCobro.rechazado(pago.idCargoPasarela(), CODIGO_VENCIDO,
                    "El código de pago venció sin pagarse; genera uno nuevo", Map.of()), ahora);
        }
        return false;
    }

    private boolean aplicar(Pago pago, ResultadoCobro resultado, Instant ahora) {
        Pago actualizado = pago.aplicar(resultado, ahora);
        if (actualizado == pago) {
            return false;
        }
        pagos.actualizar(actualizado);
        REGISTRO.info("Pago {} conciliado: {} ({})", pago.idPago(), actualizado.estadoPago(), resultado.codigoRespuesta());
        if (actualizado.estadoPago() == EstadoPago.APROBADO) {
            try {
                emitirComprobante.emitirPara(pago.idPago());
            } catch (RuntimeException e) {
                REGISTRO.error("No se pudo emitir el comprobante del pago {}: {}", pago.idPago(), e.getMessage());
            }
        }
        return true;
    }
}
