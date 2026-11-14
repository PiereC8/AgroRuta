package pe.agroruta.application.service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import pe.agroruta.domain.model.Cotizacion;
import pe.agroruta.domain.model.CotizadorDestinos;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.model.ReglasLote;
import pe.agroruta.domain.port.in.CotizarFleteUseCase;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.PreciosDestinoPort;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.TarifaPort;

public class CotizarFleteService implements CotizarFleteUseCase {

    private final LoteRepositoryPort lotes;
    private final DestinoRepositoryPort destinos;
    private final PreciosDestinoPort precios;
    private final TarifaPort tarifas;
    private final PedidoRepositoryPort pedidos;

    public CotizarFleteService(LoteRepositoryPort lotes, DestinoRepositoryPort destinos, PreciosDestinoPort precios,
                               TarifaPort tarifas, PedidoRepositoryPort pedidos) {
        this.pedidos = pedidos;
        this.lotes = lotes;
        this.destinos = destinos;
        this.precios = precios;
        this.tarifas = tarifas;
    }

    @Override
    public Cotizacion cotizar(Long idLote) {
        Lote lote = lotes.buscarPorId(idLote).orElseThrow(() -> new LoteNoEncontradoException(idLote));
        BigDecimal disponibles = ReglasLote.kgDisponibles(lote, pedidos.vigentesDeLote(idLote));
        if (disponibles.signum() <= 0) {
            throw new DominioException("El lote no tiene kg disponibles: todo está comprometido en pedidos");
        }
        List<DestinoVenta> activos = destinos.listarActivos();
        Map<Long, Dinero> preciosPorDestino = precios.preciosEsperadosPorKg(lote.productoLote());
        List<Long> destinosConFlete = activos.stream()
                .filter(destino -> !destino.esLocal() && preciosPorDestino.containsKey(destino.idDestinoVenta()))
                .map(DestinoVenta::idDestinoVenta)
                .toList();
        Map<Long, Dinero> fletes = tarifas.costosFletePorKg(destinosConFlete);
        return CotizadorDestinos.cotizar(lote, disponibles, activos, preciosPorDestino, fletes);
    }
}
