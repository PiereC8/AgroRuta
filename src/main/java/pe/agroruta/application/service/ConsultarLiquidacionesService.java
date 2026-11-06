package pe.agroruta.application.service;

import java.util.List;

import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Paginacion;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.port.in.ConsultarLiquidacionesUseCase;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;

public class ConsultarLiquidacionesService implements ConsultarLiquidacionesUseCase {

    private final LiquidacionRepositoryPort liquidaciones;

    public ConsultarLiquidacionesService(LiquidacionRepositoryPort liquidaciones) {
        this.liquidaciones = liquidaciones;
    }

    @Override
    public Liquidacion obtenerPorLote(Long idLote) {
        return liquidaciones.buscarPorLote(idLote)
                .orElseThrow(() -> new RecursoNoEncontradoException("El lote " + idLote + " todavía no tiene liquidación"));
    }

    @Override
    public List<Liquidacion> listar(int pagina, int tamano) {
        Paginacion.validar(pagina, tamano);
        return liquidaciones.listar(pagina, tamano);
    }
}
