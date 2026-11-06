package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoLote;
import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.model.Paginacion;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.model.Vehiculo;
import pe.agroruta.domain.port.in.DespacharEnvioUseCase;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.TransaccionPort;
import pe.agroruta.domain.port.out.TransporteRepositoryPort;

public class DespacharEnvioService implements DespacharEnvioUseCase {

    private final PedidoRepositoryPort pedidos;
    private final LoteRepositoryPort lotes;
    private final TransporteRepositoryPort transporte;
    private final EnvioRepositoryPort envios;
    private final TransaccionPort transaccion;
    private final Clock reloj;

    public DespacharEnvioService(PedidoRepositoryPort pedidos, LoteRepositoryPort lotes,
                                 TransporteRepositoryPort transporte, EnvioRepositoryPort envios,
                                 TransaccionPort transaccion, Clock reloj) {
        this.pedidos = pedidos;
        this.lotes = lotes;
        this.transporte = transporte;
        this.envios = envios;
        this.transaccion = transaccion;
        this.reloj = reloj;
    }

    @Override
    public Envio despachar(Long idPedido, Long idVehiculo, String usuario) {
        Pedido pedido = pedidos.buscarPorId(idPedido)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido " + idPedido));
        Pedido despachado = pedido.despachado();
        Lote lote = lotes.buscarPorId(pedido.idLote())
                .orElseThrow(() -> new LoteNoEncontradoException(pedido.idLote()));
        lote.reservar();
        Vehiculo vehiculo = transporte.buscarVehiculo(idVehiculo)
                .filter(Vehiculo::vehiculoActivo)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el vehículo " + idVehiculo));
        vehiculo.verificarCapacidad(lote.cantidadKg());
        if (envios.existeActivoDeVehiculo(vehiculo.idVehiculo())) {
            throw new ConflictoDominioException("El vehículo " + vehiculo.placaVehiculo() + " ya tiene un envío en curso");
        }

        Instant ahora = reloj.instant();
        Envio envio = Envio.programar(pedido, vehiculo, usuario, ahora);
        EventoLote evento = new EventoLote(TipoEventoLote.RESERVA,
                "Lote reservado para el pedido " + pedido.idPedido() + " en el vehículo " + vehiculo.placaVehiculo(),
                usuario, ahora);
        return transaccion.ejecutar(() -> {
            Envio guardado = envios.guardarNuevo(envio);
            pedidos.actualizarEstado(despachado.idPedido(), EstadoPedido.DESPACHADO);
            lotes.actualizarEstado(lote.idLote(), EstadoLote.RESERVADO, evento);
            return guardado;
        });
    }

    @Override
    public Envio iniciarRuta(Long idEnvio, String usuario) {
        Envio envio = obtener(idEnvio);
        Instant ahora = reloj.instant();
        Envio enRuta = envio.iniciarRuta(ahora);
        Lote lote = lotes.buscarPorId(envio.idLote())
                .orElseThrow(() -> new LoteNoEncontradoException(envio.idLote()));
        lote.despachar();
        EventoLote evento = new EventoLote(TipoEventoLote.DESPACHO,
                "El envío " + envio.idEnvio() + " salió en ruta", usuario, ahora);
        transaccion.ejecutar(() -> {
            envios.actualizar(enRuta);
            lotes.actualizarEstado(lote.idLote(), EstadoLote.DESPACHADO, evento);
            return enRuta;
        });
        return enRuta;
    }

    @Override
    public List<Envio> listar(int pagina, int tamano) {
        Paginacion.validar(pagina, tamano);
        return envios.listar(pagina, tamano);
    }

    @Override
    public Envio obtener(Long idEnvio) {
        return envios.buscarPorId(idEnvio)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + idEnvio));
    }
}
