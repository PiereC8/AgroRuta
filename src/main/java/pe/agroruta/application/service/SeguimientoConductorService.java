package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;

import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.Posicion;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.ReglasSeguimiento;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoAlerta;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.model.Trayecto;
import pe.agroruta.domain.port.in.SeguimientoConductorUseCase;
import pe.agroruta.domain.port.out.UbicacionDestinoPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.PublicadorSeguimientoPort;
import pe.agroruta.domain.port.out.RutaPort;
import pe.agroruta.domain.port.out.RutaRepositoryPort;
import pe.agroruta.domain.port.out.SeguimientoRepositoryPort;

public class SeguimientoConductorService implements SeguimientoConductorUseCase {

    /** Una posición recién tomada: las más viejas son de una cola enviada tras recuperar la señal. */
    static final Duration FRESCURA = Duration.ofMinutes(2);
    static final Duration ESPERA_ENTRE_AVISOS_GPS = Duration.ofMinutes(5);

    private final EnvioRepositoryPort envios;
    private final PedidoRepositoryPort pedidos;
    private final UbicacionDestinoPort ubicaciones;
    private final SeguimientoRepositoryPort seguimiento;
    private final PublicadorSeguimientoPort publicador;
    private final RutaPort calculadorRutas;
    private final RutaRepositoryPort rutas;
    private final Clock reloj;

    public SeguimientoConductorService(EnvioRepositoryPort envios, PedidoRepositoryPort pedidos,
                                       UbicacionDestinoPort ubicaciones, SeguimientoRepositoryPort seguimiento,
                                       PublicadorSeguimientoPort publicador, RutaPort calculadorRutas,
                                       RutaRepositoryPort rutas, Clock reloj) {
        this.envios = envios;
        this.pedidos = pedidos;
        this.ubicaciones = ubicaciones;
        this.seguimiento = seguimiento;
        this.publicador = publicador;
        this.calculadorRutas = calculadorRutas;
        this.rutas = rutas;
        this.reloj = reloj;
    }

    @Override
    public void iniciar(SesionEnlace sesion, boolean gpsActivo, boolean permisoUbicacion, boolean consentimiento) {
        Envio envio = envioDelConductor(sesion);
        if (envio.estadoEnvio() != EstadoEnvio.EN_RUTA) {
            throw new ConflictoDominioException("El envío " + envio.idEnvio()
                    + " no está en ruta: la ubicación solo se comparte durante la ruta");
        }
        if (!consentimiento) {
            throw new DominioException("Debes aceptar compartir tu ubicación para iniciar");
        }
        if (!gpsActivo || !permisoUbicacion) {
            throw new ConflictoDominioException("Activa el GPS y el permiso de ubicación del celular antes de iniciar");
        }
        seguimiento.registrarConsentimiento(envio.idEnvio(), reloj.instant());
    }

    @Override
    public ResultadoPosicion reportarPosicion(SesionEnlace sesion, double latitud, double longitud,
                                              Double precisionMetros, Instant fechaPosicion) {
        Envio envio = envioDelConductor(sesion);
        if (!seguimiento.tieneConsentimiento(envio.idEnvio())) {
            throw new ConflictoDominioException("El conductor todavía no aceptó compartir su ubicación");
        }
        Instant ahora = reloj.instant();
        Posicion posicion = Posicion.nueva(envio.idEnvio(), latitud, longitud, precisionMetros, fechaPosicion, ahora);
        if (!envio.admitePosicionEn(posicion.fechaPosicion())) {
            throw new DominioException("La posición no corresponde al tiempo en que el envío estuvo en ruta");
        }
        Optional<Posicion> guardada = seguimiento.guardarPosicion(posicion);
        if (guardada.isEmpty()) {
            return ResultadoPosicion.DUPLICADA;
        }

        boolean reciente = !posicion.fechaPosicion().isBefore(ahora.minus(FRESCURA));
        Optional<Coordenada> destino = coordenadaDestino(envio);
        OptionalLong eta = OptionalLong.empty();
        Trayecto trayecto = null;
        if (reciente && destino.isPresent()) {
            trayecto = asegurarRuta(sesion, envio, posicion.coordenada(), destino.get(), ahora);
            List<Posicion> ultimas = seguimiento.posicionesDesde(envio.idEnvio(),
                    posicion.fechaPosicion().minus(ReglasSeguimiento.VENTANA_VELOCIDAD));
            eta = ReglasSeguimiento.etaMinutos(ultimas, destino.get(), trayecto);
        }
        publicador.publicarPosicion(sesion.identificadorNegocio(), envio.idEnvio(), guardada.get(),
                eta.isPresent() ? eta.getAsLong() : null);

        if (reciente && envio.estadoEnvio() == EstadoEnvio.EN_RUTA) {
            avisarSiLlego(sesion, envio, posicion, destino, ahora);
            avisarSiSeDetuvo(sesion, envio, ahora);
            avisarSiHayRetraso(sesion, envio, trayecto, eta, ahora);
        }
        return ResultadoPosicion.ACEPTADA;
    }

    @Override
    public void reportarEstadoGps(SesionEnlace sesion, boolean gpsActivo) {
        if (gpsActivo) {
            return;
        }
        Envio envio = envioDelConductor(sesion);
        if (envio.estadoEnvio() != EstadoEnvio.EN_RUTA) {
            return;
        }
        Instant ahora = reloj.instant();
        if (seguimiento.existeAlertaDesde(envio.idEnvio(), TipoAlerta.GPS_DESACTIVADO,
                ahora.minus(ESPERA_ENTRE_AVISOS_GPS))) {
            return;
        }
        alertar(sesion, envio, TipoAlerta.GPS_DESACTIVADO,
                "El GPS o el permiso de ubicación del celular del conductor se desactivó", ahora);
    }

    /**
     * Mantiene la ruta desde donde está el vehículo hasta el destino. Se calcula con la primera posición y se
     * recalcula si el vehículo se sale de ella, o si solo había una línea recta de respaldo. Nunca falla: si el
     * servicio de rutas no responde, queda la línea recta.
     */
    private Trayecto asegurarRuta(SesionEnlace sesion, Envio envio, Coordenada origen, Coordenada destino,
                                  Instant ahora) {
        Optional<Trayecto> actual = rutas.buscar(envio.idEnvio());
        if (actual.isPresent() && !necesitaRutaNueva(actual.get(), origen, destino, ahora)) {
            return actual.get();
        }
        Trayecto nuevo;
        try {
            nuevo = calculadorRutas.calcular(origen, destino, ahora);
        } catch (ServicioExternoNoDisponibleException | DominioException e) {
            nuevo = Trayecto.lineaRecta(origen, destino, ahora);
        }
        rutas.guardar(envio.idEnvio(), nuevo);
        publicador.publicarRuta(sesion.identificadorNegocio(), envio.idEnvio());
        return nuevo;
    }

    private boolean necesitaRutaNueva(Trayecto actual, Coordenada posicion, Coordenada destino, Instant ahora) {
        if (ReglasSeguimiento.llego(posicion, destino)) {
            return false;
        }
        if (ReglasSeguimiento.desviado(actual, posicion)) {
            return true;
        }
        return !actual.porCarretera()
                && Duration.between(actual.fechaCalculo(), ahora).compareTo(ReglasSeguimiento.REINTENTO_RUTA) >= 0;
    }

    private void avisarSiHayRetraso(SesionEnlace sesion, Envio envio, Trayecto trayecto, OptionalLong eta,
                                    Instant ahora) {
        if (trayecto == null || eta.isEmpty()) {
            return;
        }
        Duration duracion = Duration.ofSeconds(trayecto.duracionSegundos());
        Instant prevista = trayecto.fechaCalculo().plus(duracion);
        Instant estimada = ahora.plus(Duration.ofMinutes(eta.getAsLong()));
        if (!ReglasSeguimiento.hayRetraso(prevista, estimada, duracion)) {
            return;
        }
        if (!seguimiento.existeAlertaDesde(envio.idEnvio(), TipoAlerta.RETRASO, trayecto.fechaCalculo())) {
            long minutos = Duration.between(prevista, estimada).toMinutes();
            alertar(sesion, envio, TipoAlerta.RETRASO, "El envío lleva un retraso de unos " + minutos
                    + " minutos respecto de lo previsto", ahora);
        }
    }

    private void avisarSiLlego(SesionEnlace sesion, Envio envio, Posicion posicion, Optional<Coordenada> destino,
                               Instant ahora) {
        if (destino.isEmpty() || !ReglasSeguimiento.llego(posicion.coordenada(), destino.get())) {
            return;
        }
        if (!seguimiento.existeAlertaDesde(envio.idEnvio(), TipoAlerta.LLEGADA, envio.fechaSalida())) {
            alertar(sesion, envio, TipoAlerta.LLEGADA, "El vehículo llegó al destino", ahora);
        }
    }

    private void avisarSiSeDetuvo(SesionEnlace sesion, Envio envio, Instant ahora) {
        Instant desde = ahora.minus(ReglasSeguimiento.VENTANA_DETENIDO).minus(ReglasSeguimiento.TOLERANCIA);
        List<Posicion> ventana = seguimiento.posicionesDesde(envio.idEnvio(), desde);
        if (!ReglasSeguimiento.detenido(ventana, ahora)) {
            return;
        }
        if (!seguimiento.existeAlertaDesde(envio.idEnvio(), TipoAlerta.DETENIDO,
                ahora.minus(ReglasSeguimiento.VENTANA_DETENIDO))) {
            alertar(sesion, envio, TipoAlerta.DETENIDO, "El vehículo lleva más de "
                    + ReglasSeguimiento.VENTANA_DETENIDO.toMinutes() + " minutos detenido", ahora);
        }
    }

    private void alertar(SesionEnlace sesion, Envio envio, TipoAlerta tipo, String descripcion, Instant ahora) {
        AlertaEnvio alerta = seguimiento.guardarAlerta(AlertaEnvio.nueva(envio.idEnvio(), tipo, descripcion, ahora));
        publicador.publicarAlerta(sesion.identificadorNegocio(), envio.idEnvio(), alerta);
    }

    private Optional<Coordenada> coordenadaDestino(Envio envio) {
        return pedidos.buscarPorId(envio.idPedido()).map(Pedido::idDestinoVenta)
                .flatMap(ubicaciones::buscarCoordenada);
    }

    private Envio envioDelConductor(SesionEnlace sesion) {
        if (sesion.tipoEnlace() != TipoEnlace.CONDUCTOR) {
            throw new DominioException("Este enlace no permite compartir ubicación");
        }
        return envios.buscarPorId(sesion.idEnvio())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + sesion.idEnvio()));
    }
}
