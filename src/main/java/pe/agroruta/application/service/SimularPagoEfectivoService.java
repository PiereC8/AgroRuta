package pe.agroruta.application.service;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.ConfirmarPagoUseCase;
import pe.agroruta.domain.port.in.SimularPagoEfectivoUseCase;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;
import pe.agroruta.domain.port.out.PagoPort;
import pe.agroruta.domain.port.out.PagoRepositoryPort;

public class SimularPagoEfectivoService implements SimularPagoEfectivoUseCase {

    private final EnvioRepositoryPort envios;
    private final LiquidacionRepositoryPort liquidaciones;
    private final PagoRepositoryPort pagos;
    private final PagoPort pasarela;
    private final ConfirmarPagoUseCase confirmarPago;

    public SimularPagoEfectivoService(EnvioRepositoryPort envios, LiquidacionRepositoryPort liquidaciones,
                                      PagoRepositoryPort pagos, PagoPort pasarela, ConfirmarPagoUseCase confirmarPago) {
        this.envios = envios;
        this.liquidaciones = liquidaciones;
        this.pagos = pagos;
        this.pasarela = pasarela;
        this.confirmarPago = confirmarPago;
    }

    @Override
    public void simular(SesionEnlace sesion) {
        if (sesion == null || sesion.tipoEnlace() != TipoEnlace.COMPRADOR) {
            throw new DominioException("Este enlace no permite pagar");
        }
        Envio envio = envios.buscarPorId(sesion.idEnvio())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + sesion.idEnvio()));
        Liquidacion liquidacion = liquidaciones.buscarPorEnvio(envio.idEnvio())
                .orElseThrow(() -> new ConflictoDominioException("El pedido todavía no tiene liquidación"));
        Pago pago = pagos.ultimoDeLiquidacion(liquidacion.idLiquidacion())
                .filter(p -> p.estadoPago() == EstadoPago.PENDIENTE
                        && ResultadoCobro.CIP_GENERADO.equals(p.codigoRespuesta()))
                .orElseThrow(() -> new ConflictoDominioException("No hay un código de pago pendiente"));
        if (!pasarela.simularPagoEnAgente(pago.idCargoPasarela())) {
            throw new DominioException("Solo se puede simular el pago con la pasarela simulada");
        }
        confirmarPago.confirmar(pago.idCargoPasarela());
    }
}
