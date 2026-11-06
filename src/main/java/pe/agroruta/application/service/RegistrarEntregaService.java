package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.DestinoNoEncontradoException;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoLote;
import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.RegistrarEntregaUseCase;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.TarifaPort;
import pe.agroruta.domain.port.out.TransaccionPort;

public class RegistrarEntregaService implements RegistrarEntregaUseCase {

    private final EnvioRepositoryPort envios;
    private final PedidoRepositoryPort pedidos;
    private final LoteRepositoryPort lotes;
    private final DestinoRepositoryPort destinos;
    private final TarifaPort tarifas;
    private final LiquidacionRepositoryPort liquidaciones;
    private final TransaccionPort transaccion;
    private final Clock reloj;

    public RegistrarEntregaService(EnvioRepositoryPort envios, PedidoRepositoryPort pedidos,
                                   LoteRepositoryPort lotes, DestinoRepositoryPort destinos, TarifaPort tarifas,
                                   LiquidacionRepositoryPort liquidaciones, TransaccionPort transaccion,
                                   Clock reloj) {
        this.envios = envios;
        this.pedidos = pedidos;
        this.lotes = lotes;
        this.destinos = destinos;
        this.tarifas = tarifas;
        this.liquidaciones = liquidaciones;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    @Override
    public Liquidacion registrar(Comando comando) {
        Envio envio = envios.buscarPorId(comando.idEnvio())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + comando.idEnvio()));
        Instant ahora = reloj.instant();
        Envio entregado = envio.marcarEntregado(ahora);
        Pedido pedido = pedidos.buscarPorId(envio.idPedido())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido " + envio.idPedido()));
        pedido.entregado();
        Lote lote = lotes.buscarPorId(envio.idLote())
                .orElseThrow(() -> new LoteNoEncontradoException(envio.idLote()));
        lote.entregar().liquidar();

        Dinero fletePorKg = costoFletePorKg(pedido);
        Liquidacion liquidacion = Liquidacion.calcular(pedido, envio, comando.cantidadRecibidaKg(),
                comando.mermaKg(), comando.rechazoKg(), fletePorKg, comando.usuario(), ahora);

        EventoLote eventoEntrega = new EventoLote(TipoEventoLote.ENTREGA,
                "Entrega registrada: recibido " + liquidacion.cantidadRecibidaKg().toPlainString()
                        + " kg, merma " + liquidacion.mermaKg().toPlainString()
                        + " kg, rechazo " + liquidacion.rechazoKg().toPlainString() + " kg",
                comando.usuario(), ahora);
        EventoLote eventoLiquidacion = new EventoLote(TipoEventoLote.LIQUIDACION,
                "Ganancia real S/ " + liquidacion.gananciaReal().monto().toPlainString()
                        + " (esperada S/ " + liquidacion.gananciaEsperada().monto().toPlainString() + ")",
                comando.usuario(), ahora);

        return transaccion.ejecutar(() -> {
            envios.actualizar(entregado);
            pedidos.actualizarEstado(pedido.idPedido(), EstadoPedido.ENTREGADO);
            lotes.actualizarEstado(lote.idLote(), EstadoLote.ENTREGADO, eventoEntrega);
            lotes.actualizarEstado(lote.idLote(), EstadoLote.LIQUIDADO, eventoLiquidacion);
            return liquidaciones.guardarNueva(liquidacion);
        });
    }

    private Dinero costoFletePorKg(Pedido pedido) {
        DestinoVenta destino = destinos.buscarPorId(pedido.idDestinoVenta())
                .orElseThrow(() -> new DestinoNoEncontradoException(pedido.idDestinoVenta()));
        if (destino.esLocal()) {
            return Dinero.CERO;
        }
        Dinero tarifa = tarifas.costosFletePorKg(List.of(destino.idDestinoVenta())).get(destino.idDestinoVenta());
        if (tarifa == null) {
            throw new ConflictoDominioException("El destino " + destino.nombreDestino()
                    + " no tiene tarifa de flete: fíjala antes de registrar la entrega");
        }
        return tarifa;
    }
}
