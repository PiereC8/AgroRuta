package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;

import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.model.ReglasLote;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.CancelarOperacionUseCase;
import pe.agroruta.domain.port.out.EnlaceEnvioRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.TransaccionPort;

public class CancelarOperacionService implements CancelarOperacionUseCase {

    private final EnvioRepositoryPort envios;
    private final PedidoRepositoryPort pedidos;
    private final LoteRepositoryPort lotes;
    private final EnlaceEnvioRepositoryPort enlaces;
    private final TransaccionPort transaccion;
    private final Clock reloj;

    public CancelarOperacionService(EnvioRepositoryPort envios, PedidoRepositoryPort pedidos, LoteRepositoryPort lotes,
                                    EnlaceEnvioRepositoryPort enlaces, TransaccionPort transaccion, Clock reloj) {
        this.envios = envios;
        this.pedidos = pedidos;
        this.lotes = lotes;
        this.enlaces = enlaces;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /**
     * El envío queda cancelado, el pedido vuelve a pendiente (se puede despachar en otro vehículo) y conserva sus kg
     * reservados en el lote, y los enlaces del conductor y del comprador dejan de valer.
     */
    @Override
    public Envio cancelarEnvio(String identificadorNegocio, Long idEnvio, String usuario) {
        Envio envio = envios.buscarPorId(idEnvio)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + idEnvio));
        Envio cancelado = envio.cancelar();
        Pedido pedido = pedidos.buscarPorId(envio.idPedido())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido " + envio.idPedido()));
        pedido.volverAPendiente();
        Lote lote = lotes.buscarPorId(envio.idLote()).orElseThrow(() -> new LoteNoEncontradoException(envio.idLote()));
        Instant ahora = reloj.instant();
        String etapa = envio.estadoEnvio() == EstadoEnvio.EN_RUTA ? "en ruta" : "programado";
        EventoLote evento = new EventoLote(TipoEventoLote.CANCELACION, "Se canceló el envío " + idEnvio + " ("
                + etapa + "); el pedido " + pedido.idPedido() + " queda pendiente de despacho", usuario, ahora);
        transaccion.ejecutar(() -> {
            envios.actualizar(cancelado);
            pedidos.actualizarEstado(pedido.idPedido(), EstadoPedido.PENDIENTE);
            lotes.actualizarEstado(lote.idLote(), ReglasLote.estadoSegun(lote, pedidos.vigentesDeLote(lote.idLote()),
                    envios.existeEnRutaDeLote(lote.idLote())), evento);
            return cancelado;
        });
        enlaces.desactivarDeEnvio(identificadorNegocio, idEnvio, TipoEnlace.CONDUCTOR);
        enlaces.desactivarDeEnvio(identificadorNegocio, idEnvio, TipoEnlace.COMPRADOR);
        return cancelado;
    }

    /** Solo un pedido sin envío en curso; sus kg vuelven a quedar disponibles en el lote. */
    @Override
    public Pedido cancelarPedido(Long idPedido, String usuario) {
        Pedido pedido = pedidos.buscarPorId(idPedido)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido " + idPedido));
        Pedido cancelado = pedido.cancelar();
        Lote lote = lotes.buscarPorId(pedido.idLote()).orElseThrow(() -> new LoteNoEncontradoException(pedido.idLote()));
        EventoLote evento = new EventoLote(TipoEventoLote.CANCELACION, "Se canceló el pedido " + idPedido + "; sus "
                + pedido.cantidadKg().toPlainString() + " kg vuelven a estar disponibles en el lote", usuario,
                reloj.instant());
        transaccion.ejecutar(() -> {
            pedidos.actualizarEstado(idPedido, EstadoPedido.CANCELADO);
            lotes.actualizarEstado(lote.idLote(), ReglasLote.estadoSegun(lote, pedidos.vigentesDeLote(lote.idLote()),
                    envios.existeEnRutaDeLote(lote.idLote())), evento);
            return cancelado;
        });
        return cancelado;
    }
}
