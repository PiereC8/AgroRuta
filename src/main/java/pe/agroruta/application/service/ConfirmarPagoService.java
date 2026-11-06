package pe.agroruta.application.service;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.port.in.ConfirmarPagoUseCase;
import pe.agroruta.domain.port.in.EmitirComprobanteUseCase;
import pe.agroruta.domain.port.out.EjecutorPorNegocioPort;
import pe.agroruta.domain.port.out.PagoPort;
import pe.agroruta.domain.port.out.PagoRepositoryPort;

public class ConfirmarPagoService implements ConfirmarPagoUseCase {

    private static final Logger REGISTRO = LoggerFactory.getLogger(ConfirmarPagoService.class);
    private static final Pattern FORMATO_NEGOCIO = Pattern.compile("^[a-z][a-z0-9_]{2,30}$");
    private static final Pattern FORMATO_ID = Pattern.compile("^\\d{1,18}$");

    private final PagoPort pasarela;
    private final PagoRepositoryPort pagos;
    private final EmitirComprobanteUseCase emitirComprobante;
    private final EjecutorPorNegocioPort ejecutor;
    private final Clock reloj;

    public ConfirmarPagoService(PagoPort pasarela, PagoRepositoryPort pagos, EmitirComprobanteUseCase emitirComprobante,
                                EjecutorPorNegocioPort ejecutor, Clock reloj) {
        this.pasarela = pasarela;
        this.pagos = pagos;
        this.emitirComprobante = emitirComprobante;
        this.ejecutor = ejecutor;
        this.reloj = reloj;
    }

    @Override
    public boolean confirmar(String idCargo) {
        Optional<ResultadoCobro> cargo = pasarela.consultarCargo(idCargo);
        if (cargo.isEmpty()) {
            REGISTRO.warn("La pasarela no reconoce el cargo {}: se ignora la notificación", idCargo);
            return false;
        }
        Map<String, String> metadatos = cargo.get().metadatos();
        String negocio = metadatos == null ? null : metadatos.get("negocio");
        String idPago = metadatos == null ? null : metadatos.get("idPago");
        if (negocio == null || !FORMATO_NEGOCIO.matcher(negocio).matches()
                || idPago == null || !FORMATO_ID.matcher(idPago).matches()) {
            REGISTRO.warn("El cargo {} no trae los datos de AgroRuta: se ignora", idCargo);
            return false;
        }
        return ejecutor.ejecutarEn(negocio, () -> aplicar(Long.valueOf(idPago), idCargo, cargo.get()));
    }

    private boolean aplicar(Long idPago, String idCargo, ResultadoCobro cargo) {
        Optional<Pago> encontrado = pagos.buscarPorId(idPago);
        if (encontrado.isEmpty()) {
            return false;
        }
        Pago pago = encontrado.get();
        if (pago.idCargoPasarela() != null && !pago.idCargoPasarela().equals(idCargo)) {
            REGISTRO.warn("El cargo {} no corresponde al pago {}", idCargo, idPago);
            return false;
        }
        Pago actualizado = pago.aplicar(cargo, reloj.instant());
        if (actualizado != pago) {
            pagos.actualizar(actualizado);
        }
        if (actualizado.estadoPago() == EstadoPago.APROBADO) {
            try {
                emitirComprobante.emitirPara(idPago);
            } catch (RuntimeException e) {
                REGISTRO.error("No se pudo emitir el comprobante del pago {}: {}", idPago, e.getMessage());
            }
        }
        return true;
    }
}
