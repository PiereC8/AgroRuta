package pe.agroruta.application.service;

import java.util.List;
import java.util.Map;

import pe.agroruta.domain.model.Cotizacion;
import pe.agroruta.domain.model.CotizadorDestinos;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.EstadoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.port.in.CotizarFleteUseCase;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.TarifaPort;

public class CotizarFleteService implements CotizarFleteUseCase {

    private final LoteRepositoryPort lotes;
    private final DestinoRepositoryPort destinos;
    private final TarifaPort tarifas;

    public CotizarFleteService(LoteRepositoryPort lotes, DestinoRepositoryPort destinos, TarifaPort tarifas) {
        this.lotes = lotes;
        this.destinos = destinos;
        this.tarifas = tarifas;
    }

    @Override
    public Cotizacion cotizar(Long idLote) {
        Lote lote = lotes.buscarPorId(idLote).orElseThrow(() -> new LoteNoEncontradoException(idLote));
        if (lote.estadoLote() != EstadoLote.REGISTRADO) {
            throw new DominioException("Solo se cotizan lotes que todavía no fueron reservados ni despachados");
        }
        List<DestinoVenta> activos = destinos.listarActivos();
        Map<Long, Dinero> precios = destinos.preciosEsperadosPorKg(lote.productoLote());
        List<Long> destinosConFlete = activos.stream()
                .filter(destino -> !destino.esLocal() && precios.containsKey(destino.idDestinoVenta()))
                .map(DestinoVenta::idDestinoVenta)
                .toList();
        Map<Long, Dinero> fletes = tarifas.costosFletePorKg(destinosConFlete);
        return CotizadorDestinos.cotizar(lote, activos, precios, fletes);
    }
}
