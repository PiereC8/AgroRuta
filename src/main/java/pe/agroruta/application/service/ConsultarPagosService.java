package pe.agroruta.application.service;

import java.util.List;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.Paginacion;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.port.in.ConsultarPagosUseCase;
import pe.agroruta.domain.port.out.ComprobanteRepositoryPort;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;
import pe.agroruta.domain.port.out.PagoRepositoryPort;

public class ConsultarPagosService implements ConsultarPagosUseCase {

    private final PagoRepositoryPort pagos;
    private final ComprobanteRepositoryPort comprobantes;
    private final LiquidacionRepositoryPort liquidaciones;

    public ConsultarPagosService(PagoRepositoryPort pagos, ComprobanteRepositoryPort comprobantes,
                                 LiquidacionRepositoryPort liquidaciones) {
        this.pagos = pagos;
        this.comprobantes = comprobantes;
        this.liquidaciones = liquidaciones;
    }

    @Override
    public List<Pago> listar(int pagina, int tamano) {
        Paginacion.validar(pagina, tamano);
        return pagos.listar(pagina, tamano);
    }

    @Override
    public Pago pagoDeEnvio(Long idEnvio) {
        return liquidaciones.buscarPorEnvio(idEnvio)
                .flatMap(liquidacion -> pagos.ultimoDeLiquidacion(liquidacion.idLiquidacion()))
                .orElseThrow(() -> new RecursoNoEncontradoException("El envío " + idEnvio + " todavía no tiene pagos"));
    }

    @Override
    public Comprobante comprobanteDePago(Long idPago) {
        return comprobantes.buscarPorPago(idPago).orElseThrow(
                () -> new RecursoNoEncontradoException("El pago " + idPago + " todavía no tiene comprobante"));
    }
}
