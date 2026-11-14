package pe.agroruta.application.service;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.DestinoNoEncontradoException;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.model.Paginacion;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.ReglasLote;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.GestionarPedidosUseCase;
import pe.agroruta.domain.port.out.CompradorRepositoryPort;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.TransaccionPort;

public class GestionarPedidosService implements GestionarPedidosUseCase {

    private final PedidoRepositoryPort pedidos;
    private final CompradorRepositoryPort compradores;
    private final LoteRepositoryPort lotes;
    private final DestinoRepositoryPort destinos;
    private final EnvioRepositoryPort envios;
    private final TransaccionPort transaccion;
    private final Clock reloj;

    public GestionarPedidosService(PedidoRepositoryPort pedidos, CompradorRepositoryPort compradores,
                                   LoteRepositoryPort lotes, DestinoRepositoryPort destinos,
                                   EnvioRepositoryPort envios, TransaccionPort transaccion, Clock reloj) {
        this.pedidos = pedidos;
        this.compradores = compradores;
        this.lotes = lotes;
        this.destinos = destinos;
        this.envios = envios;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /**
     * Reserva los kg del pedido sobre el stock del lote. La fila del lote se bloquea mientras se calcula lo disponible,
     * para que dos pedidos a la vez no reserven los mismos kg.
     */
    @Override
    public Pedido registrar(Comando comando) {
        Comprador comprador = compradores.buscarPorId(comando.idComprador())
                .filter(Comprador::compradorActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el comprador " + comando.idComprador()));
        DestinoVenta destino = destinos.buscarPorId(comando.idDestinoVenta())
                .filter(DestinoVenta::destinoActivo)
                .orElseThrow(() -> new DestinoNoEncontradoException(comando.idDestinoVenta()));
        Dinero precio = comando.precioAcordadoPorKg() == null ? null : new Dinero(comando.precioAcordadoPorKg());
        Instant ahora = reloj.instant();

        return transaccion.ejecutar(() -> {
            Lote lote = lotes.buscarParaActualizar(comando.idLote())
                    .orElseThrow(() -> new LoteNoEncontradoException(comando.idLote()));
            List<Pedido> vigentes = pedidos.vigentesDeLote(lote.idLote());
            BigDecimal disponibles = ReglasLote.kgDisponibles(lote, vigentes);
            BigDecimal cantidad = comando.cantidadKg() == null ? disponibles : comando.cantidadKg();

            Pedido guardado = pedidos.guardarNuevo(Pedido.nuevo(comprador.idComprador(), lote, disponibles,
                    destino.idDestinoVenta(), cantidad, precio, comando.usuarioRegistro(), ahora));

            List<Pedido> conElNuevo = new ArrayList<>(vigentes);
            conElNuevo.add(guardado);
            EventoLote evento = new EventoLote(TipoEventoLote.RESERVA, "El pedido " + guardado.idPedido()
                    + " reservó " + guardado.cantidadKg().toPlainString() + " kg para " + comprador.nombreComprador()
                    + "; quedan " + ReglasLote.kgDisponibles(lote, conElNuevo).toPlainString() + " kg disponibles",
                    comando.usuarioRegistro(), ahora);
            lotes.actualizarEstado(lote.idLote(),
                    ReglasLote.estadoSegun(lote, conElNuevo, envios.existeEnRutaDeLote(lote.idLote())), evento);
            return guardado;
        });
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
