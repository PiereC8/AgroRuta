package pe.agroruta.application.service;

import java.time.Clock;
import java.util.List;

import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.DestinoNoEncontradoException;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.model.Paginacion;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.port.in.GestionarPedidosUseCase;
import pe.agroruta.domain.port.out.CompradorRepositoryPort;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;

public class GestionarPedidosService implements GestionarPedidosUseCase {

    private final PedidoRepositoryPort pedidos;
    private final CompradorRepositoryPort compradores;
    private final LoteRepositoryPort lotes;
    private final DestinoRepositoryPort destinos;
    private final Clock reloj;

    public GestionarPedidosService(PedidoRepositoryPort pedidos, CompradorRepositoryPort compradores,
                                   LoteRepositoryPort lotes, DestinoRepositoryPort destinos, Clock reloj) {
        this.pedidos = pedidos;
        this.compradores = compradores;
        this.lotes = lotes;
        this.destinos = destinos;
        this.reloj = reloj;
    }

    @Override
    public Pedido registrar(Comando comando) {
        Comprador comprador = compradores.buscarPorId(comando.idComprador())
                .filter(Comprador::compradorActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el comprador " + comando.idComprador()));
        Lote lote = lotes.buscarPorId(comando.idLote())
                .orElseThrow(() -> new LoteNoEncontradoException(comando.idLote()));
        DestinoVenta destino = destinos.buscarPorId(comando.idDestinoVenta())
                .filter(DestinoVenta::destinoActivo)
                .orElseThrow(() -> new DestinoNoEncontradoException(comando.idDestinoVenta()));

        Pedido pedido = Pedido.nuevo(comprador.idComprador(), lote, destino.idDestinoVenta(),
                comando.precioAcordadoPorKg() == null ? null : new Dinero(comando.precioAcordadoPorKg()),
                comando.usuarioRegistro(), reloj.instant());
        if (pedidos.existeParaLote(lote.idLote())) {
            throw new ConflictoDominioException("El lote " + lote.idLote() + " ya tiene un pedido");
        }
        return pedidos.guardarNuevo(pedido);
    }

    @Override
    public List<Pedido> listar(int pagina, int tamano) {
        Paginacion.validar(pagina, tamano);
        return pedidos.listar(pagina, tamano);
    }

    @Override
    public Pedido obtener(Long idPedido) {
        return pedidos.buscarPorId(idPedido)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido " + idPedido));
    }
}
