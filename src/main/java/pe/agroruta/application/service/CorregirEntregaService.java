package pe.agroruta.application.service;

import java.time.Clock;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.model.ReglasLote;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.CorregirEntregaUseCase;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.PagoRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.TransaccionPort;

public class CorregirEntregaService implements CorregirEntregaUseCase {

    private final EnvioRepositoryPort envios;
    private final PedidoRepositoryPort pedidos;
    private final LoteRepositoryPort lotes;
    private final LiquidacionRepositoryPort liquidaciones;
    private final PagoRepositoryPort pagos;
    private final TransaccionPort transaccion;
    private final Clock reloj;

    public CorregirEntregaService(EnvioRepositoryPort envios, PedidoRepositoryPort pedidos, LoteRepositoryPort lotes,
                                  LiquidacionRepositoryPort liquidaciones, PagoRepositoryPort pagos,
                                  TransaccionPort transaccion, Clock reloj) {
        this.envios = envios;
        this.pedidos = pedidos;
        this.lotes = lotes;
        this.liquidaciones = liquidaciones;
        this.pagos = pagos;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    /**
     * Recalcula la liquidación con las cantidades corregidas. Se conservan el flete y la fecha de la entrega
     * original, para que los reportes y la tarifa de ese día no cambien.
     */
    @Override
    public Liquidacion corregir(Comando comando) {
        Envio envio = envios.buscarPorId(comando.idEnvio())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + comando.idEnvio()));
        if (envio.estadoEnvio() != EstadoEnvio.ENTREGADO) {
            throw new ConflictoDominioException("El envío " + envio.idEnvio() + " todavía no se entregó");
        }
        Liquidacion actual = liquidaciones.buscarPorEnvio(envio.idEnvio())
                .orElseThrow(() -> new RecursoNoEncontradoException("El envío " + envio.idEnvio() + " no tiene liquidación"));
        if (pagos.existeAprobadoDe(actual.idLiquidacion()) || pagos.existePendienteDe(actual.idLiquidacion())) {
            throw new ConflictoDominioException(
                    "Esta entrega ya tiene un pago aprobado o en curso: ya no se puede corregir");
        }
        Pedido pedido = pedidos.buscarPorId(envio.idPedido())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido " + envio.idPedido()));
        Liquidacion corregida = Liquidacion.calcular(pedido, envio, comando.cantidadRecibidaKg(), comando.mermaKg(),
                comando.rechazoKg(), actual.costoFletePorKg(), comando.usuario(), actual.fechaEntrega())
                .conId(actual.idLiquidacion());
        EventoLote evento = new EventoLote(TipoEventoLote.CORRECCION, "Entrega corregida: recibido "
                + actual.cantidadRecibidaKg().toPlainString() + " → " + corregida.cantidadRecibidaKg().toPlainString()
                + " kg, merma " + actual.mermaKg().toPlainString() + " → " + corregida.mermaKg().toPlainString()
                + " kg, rechazo " + actual.rechazoKg().toPlainString() + " → "
                + corregida.rechazoKg().toPlainString() + " kg; ganancia real S/ "
                + corregida.gananciaReal().monto().toPlainString(), comando.usuario(), reloj.instant());
        return transaccion.ejecutar(() -> {
            liquidaciones.actualizar(corregida);
            Lote lote = lotes.buscarPorId(envio.idLote())
                    .orElseThrow(() -> new LoteNoEncontradoException(envio.idLote()));
            lotes.actualizarEstado(lote.idLote(), ReglasLote.estadoSegun(lote, pedidos.vigentesDeLote(lote.idLote()),
                    envios.existeEnRutaDeLote(lote.idLote())), evento);
            return corregida;
        });
    }
}
