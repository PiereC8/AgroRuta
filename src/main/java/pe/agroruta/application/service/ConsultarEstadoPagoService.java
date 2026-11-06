package pe.agroruta.application.service;

import java.util.Optional;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.ConsultarEstadoPagoUseCase;
import pe.agroruta.domain.port.out.ComprobanteRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;
import pe.agroruta.domain.port.out.PagoRepositoryPort;

public class ConsultarEstadoPagoService implements ConsultarEstadoPagoUseCase {

    private final EnvioRepositoryPort envios;
    private final LiquidacionRepositoryPort liquidaciones;
    private final PagoRepositoryPort pagos;
    private final ComprobanteRepositoryPort comprobantes;

    public ConsultarEstadoPagoService(EnvioRepositoryPort envios, LiquidacionRepositoryPort liquidaciones,
                                      PagoRepositoryPort pagos, ComprobanteRepositoryPort comprobantes) {
        this.envios = envios;
        this.liquidaciones = liquidaciones;
        this.pagos = pagos;
        this.comprobantes = comprobantes;
    }

    @Override
    public EstadoPagoPublico estado(SesionEnlace sesion) {
        if (sesion == null || sesion.tipoEnlace() != TipoEnlace.COMPRADOR) {
            throw new DominioException("Este enlace no permite pagar");
        }
        Envio envio = envios.buscarPorId(sesion.idEnvio())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + sesion.idEnvio()));
        String descripcion = "Pedido " + envio.idPedido();
        Optional<Liquidacion> liquidacion = liquidaciones.buscarPorLote(envio.idLote());
        if (liquidacion.isEmpty()) {
            return new EstadoPagoPublico(envio.estadoEnvio(), false, Dinero.CERO, descripcion, null, null);
        }
        Long idLiquidacion = liquidacion.get().idLiquidacion();
        Optional<Pago> ultimo = pagos.ultimoDeLiquidacion(idLiquidacion);
        boolean pagable = envio.estadoEnvio() == EstadoEnvio.ENTREGADO && liquidacion.get().ingresoReal().esPositivo()
                && !pagos.existeAprobadoDe(idLiquidacion) && !pagos.existePendienteDe(idLiquidacion);
        Comprobante comprobante = ultimo.filter(p -> p.estadoPago() == EstadoPago.APROBADO)
                .flatMap(p -> comprobantes.buscarPorPago(p.idPago())).orElse(null);
        return new EstadoPagoPublico(envio.estadoEnvio(), pagable, liquidacion.get().ingresoReal(), descripcion,
                ultimo.orElse(null), comprobante);
    }
}
