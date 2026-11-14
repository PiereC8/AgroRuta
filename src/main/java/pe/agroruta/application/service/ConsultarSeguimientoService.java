package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.Posicion;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.ReglasSeguimiento;
import pe.agroruta.domain.model.Seguimiento;
import pe.agroruta.domain.model.Trayecto;
import pe.agroruta.domain.port.in.ConsultarSeguimientoUseCase;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.RutaRepositoryPort;
import pe.agroruta.domain.port.out.SeguimientoRepositoryPort;
import pe.agroruta.domain.port.out.UbicacionDestinoPort;

public class ConsultarSeguimientoService implements ConsultarSeguimientoUseCase {

    private final EnvioRepositoryPort envios;
    private final PedidoRepositoryPort pedidos;
    private final DestinoRepositoryPort destinos;
    private final UbicacionDestinoPort ubicaciones;
    private final SeguimientoRepositoryPort seguimiento;
    private final RutaRepositoryPort rutas;
    private final Clock reloj;

    public ConsultarSeguimientoService(EnvioRepositoryPort envios, PedidoRepositoryPort pedidos,
                                       DestinoRepositoryPort destinos, UbicacionDestinoPort ubicaciones,
                                       SeguimientoRepositoryPort seguimiento, RutaRepositoryPort rutas,
                                       Clock reloj) {
        this.envios = envios;
        this.pedidos = pedidos;
        this.destinos = destinos;
        this.ubicaciones = ubicaciones;
        this.seguimiento = seguimiento;
        this.rutas = rutas;
        this.reloj = reloj;
    }

    @Override
    public Seguimiento obtener(Long idEnvio) {
        Envio envio = envio(idEnvio);
        Pedido pedido = pedidos.buscarPorId(envio.idPedido())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido " + envio.idPedido()));
        DestinoVenta destino = destinos.buscarPorId(pedido.idDestinoVenta())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el destino " + pedido.idDestinoVenta()));
        Coordenada coordenada = ubicaciones.buscarCoordenada(destino.idDestinoVenta()).orElse(null);

        List<Posicion> trazo = seguimiento.ultimasPosiciones(idEnvio, POSICIONES_DEL_TRAZO);
        Posicion ultima = trazo.isEmpty() ? null : trazo.get(trazo.size() - 1);
        Trayecto ruta = envio.estadoEnvio() == EstadoEnvio.EN_RUTA ? rutas.buscar(idEnvio).orElse(null) : null;
        Long eta = null;
        if (ultima != null && coordenada != null && envio.estadoEnvio() == EstadoEnvio.EN_RUTA) {
            OptionalLong minutos = ReglasSeguimiento.etaMinutos(trazo, coordenada, ruta);
            eta = minutos.isPresent() ? minutos.getAsLong() : null;
        }
        Double velocidad = ruta == null ? null : ReglasSeguimiento.velocidadKmh(trazo, ruta);
        Instant ahora = reloj.instant();
        return new Seguimiento(envio, destino.nombreDestino(), destino.ciudadDestino(), coordenada, ultima, eta,
                ahora, trazo, ruta, velocidad);
    }

    @Override
    public List<Posicion> recorrido(Long idEnvio) {
        envio(idEnvio);
        return seguimiento.ultimasPosiciones(idEnvio, POSICIONES_DEL_RECORRIDO);
    }

    @Override
    public List<AlertaEnvio> alertas(Long idEnvio) {
        envio(idEnvio);
        return seguimiento.alertas(idEnvio);
    }

    private Envio envio(Long idEnvio) {
        return envios.buscarPorId(idEnvio)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + idEnvio));
    }
}
