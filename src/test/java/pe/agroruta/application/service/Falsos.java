package pe.agroruta.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.time.Instant;
import java.util.Optional;
import java.util.function.Supplier;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.ResultadoEmision;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.SolicitudCobro;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.port.out.ComprobantePort;
import pe.agroruta.domain.port.out.ComprobanteRepositoryPort;
import pe.agroruta.domain.port.out.CorrelativoPort;
import pe.agroruta.domain.port.out.EjecutorPorNegocioPort;
import pe.agroruta.domain.port.out.PagoPort;
import pe.agroruta.domain.port.out.PagoRepositoryPort;
import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.EnlaceEnvio;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.model.Posicion;
import pe.agroruta.domain.model.TipoAlerta;
import pe.agroruta.domain.port.out.EnlaceEnvioRepositoryPort;
import pe.agroruta.domain.port.out.GeneradorTokenPort;
import pe.agroruta.domain.port.out.PublicadorSeguimientoPort;
import pe.agroruta.domain.port.out.SeguimientoRepositoryPort;

import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;
import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoPedido;
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.Transportista;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.model.Vehiculo;
import pe.agroruta.domain.port.out.CompradorRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.HashClavePort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.TransaccionPort;
import pe.agroruta.domain.port.out.TransporteRepositoryPort;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;

final class Falsos {

    private Falsos() {
    }

    static class NegociosEnMemoria implements NegocioRepositoryPort {
        final List<String> pasos;
        final Map<String, Negocio> registrados = new HashMap<>();

        NegociosEnMemoria(List<String> pasos) {
            this.pasos = pasos;
        }

        @Override
        public boolean existePorIdentificador(String identificadorNegocio) {
            return registrados.containsKey(identificadorNegocio);
        }

        @Override
        public Optional<Negocio> buscarActivoPorIdentificador(String identificadorNegocio) {
            return Optional.ofNullable(registrados.get(identificadorNegocio)).filter(Negocio::negocioActivo);
        }

        @Override
        public List<Negocio> listarActivos() {
            return registrados.values().stream().filter(Negocio::negocioActivo).toList();
        }

        @Override
        public Negocio guardar(Negocio negocio) {
            pasos.add("guardar:" + negocio.identificadorNegocio());
            Negocio guardado = negocio.conId(1L);
            registrados.put(negocio.identificadorNegocio(), guardado);
            return guardado;
        }
    }

    static class UsuariosEnMemoria implements UsuarioRepositoryPort {
        final List<String> pasos;
        final Map<String, Usuario> usuarios = new HashMap<>();
        private long siguienteId = 1;

        UsuariosEnMemoria() {
            this(new ArrayList<>());
        }

        UsuariosEnMemoria(List<String> pasos) {
            this.pasos = pasos;
        }

        private static String llave(String identificadorNegocio, String nombreUsuario) {
            return identificadorNegocio + "/" + nombreUsuario;
        }

        @Override
        public Optional<Usuario> buscarPorNombre(String identificadorNegocio, String nombreUsuario) {
            return Optional.ofNullable(usuarios.get(llave(identificadorNegocio, nombreUsuario)));
        }

        @Override
        public boolean existePorNombre(String identificadorNegocio, String nombreUsuario) {
            return usuarios.containsKey(llave(identificadorNegocio, nombreUsuario));
        }

        @Override
        public Usuario guardar(String identificadorNegocio, Usuario usuario) {
            pasos.add("usuario:" + usuario.nombreUsuario());
            Usuario guardado = usuario.idUsuario() == null ? usuario.conId(siguienteId++) : usuario;
            usuarios.put(llave(identificadorNegocio, usuario.nombreUsuario()), guardado);
            return guardado;
        }

        @Override
        public void cambiarClave(String identificadorNegocio, Long idUsuario, String hashClave) {
            usuarios.replaceAll((k, u) -> k.startsWith(identificadorNegocio + "/") && u.idUsuario().equals(idUsuario)
                    ? new Usuario(u.idUsuario(), u.nombreUsuario(), hashClave, u.rolUsuario(),
                            u.intentosFallidosSesion(), u.fechaBloqueoHasta(), u.usuarioActivo())
                    : u);
        }
    }

    static class CompradoresEnMemoria implements CompradorRepositoryPort {
        final List<Comprador> compradores = new ArrayList<>();

        @Override
        public boolean existePorDocumento(String documentoComprador) {
            return compradores.stream().anyMatch(c -> documentoComprador.equals(c.documentoComprador()));
        }

        @Override
        public Comprador guardar(Comprador comprador) {
            Comprador guardado = comprador.conId((long) compradores.size() + 1);
            compradores.add(guardado);
            return guardado;
        }

        @Override
        public void actualizar(Comprador comprador) {
            compradores.replaceAll(c -> c.idComprador().equals(comprador.idComprador()) ? comprador : c);
        }

        @Override
        public Optional<Comprador> buscarPorId(Long idComprador) {
            return compradores.stream().filter(c -> c.idComprador().equals(idComprador)).findFirst();
        }

        @Override
        public List<Comprador> listarActivos() {
            return compradores;
        }
    }

    static class PedidosEnMemoria implements PedidoRepositoryPort {
        final List<Pedido> pedidos = new ArrayList<>();

        @Override
        public Pedido guardarNuevo(Pedido pedido) {
            Pedido guardado = pedido.conId((long) pedidos.size() + 1);
            pedidos.add(guardado);
            return guardado;
        }

        @Override
        public Optional<Pedido> buscarPorId(Long idPedido) {
            return pedidos.stream().filter(p -> p.idPedido().equals(idPedido)).findFirst();
        }

        @Override
        public boolean existeParaLote(Long idLote) {
            return pedidos.stream().anyMatch(p -> p.idLote().equals(idLote) && p.estadoPedido() != EstadoPedido.CANCELADO);
        }

        @Override
        public List<Pedido> listar(int pagina, int tamano) {
            return pedidos.stream().skip((long) pagina * tamano).limit(tamano).toList();
        }

        @Override
        public void actualizarEstado(Long idPedido, EstadoPedido nuevoEstado) {
            for (int i = 0; i < pedidos.size(); i++) {
                Pedido p = pedidos.get(i);
                if (p.idPedido().equals(idPedido)) {
                    pedidos.set(i, new Pedido(p.idPedido(), p.idComprador(), p.idLote(), p.idDestinoVenta(),
                            p.cantidadKg(), p.precioAcordadoPorKg(), nuevoEstado, p.usuarioRegistro(), p.fechaPedido()));
                }
            }
        }
    }

    static class TransporteEnMemoria implements TransporteRepositoryPort {
        final List<Transportista> transportistas = new ArrayList<>();
        final List<Vehiculo> vehiculos = new ArrayList<>();

        @Override
        public Transportista guardarTransportista(Transportista transportista) {
            Transportista guardado = transportista.conId((long) transportistas.size() + 1);
            transportistas.add(guardado);
            return guardado;
        }

        @Override
        public Optional<Transportista> buscarTransportista(Long idTransportista) {
            return transportistas.stream().filter(t -> t.idTransportista().equals(idTransportista)).findFirst();
        }

        @Override
        public List<Transportista> listarTransportistas() {
            return transportistas;
        }

        @Override
        public boolean existeVehiculoPorPlaca(String placaVehiculo) {
            return vehiculos.stream().anyMatch(v -> v.placaVehiculo().equals(placaVehiculo));
        }

        @Override
        public Vehiculo guardarVehiculo(Vehiculo vehiculo) {
            Vehiculo guardado = vehiculo.conId((long) vehiculos.size() + 1);
            vehiculos.add(guardado);
            return guardado;
        }

        @Override
        public Optional<Vehiculo> buscarVehiculo(Long idVehiculo) {
            return vehiculos.stream().filter(v -> v.idVehiculo().equals(idVehiculo)).findFirst();
        }

        @Override
        public List<Vehiculo> listarVehiculos() {
            return vehiculos;
        }
    }

    static class EnviosEnMemoria implements EnvioRepositoryPort {
        final List<Envio> envios = new ArrayList<>();

        @Override
        public Envio guardarNuevo(Envio envio) {
            Envio guardado = envio.conId((long) envios.size() + 1);
            envios.add(guardado);
            return guardado;
        }

        @Override
        public void actualizar(Envio envio) {
            for (int i = 0; i < envios.size(); i++) {
                if (envios.get(i).idEnvio().equals(envio.idEnvio())) {
                    envios.set(i, envio);
                }
            }
        }

        @Override
        public Optional<Envio> buscarPorId(Long idEnvio) {
            return envios.stream().filter(e -> e.idEnvio().equals(idEnvio)).findFirst();
        }

        @Override
        public List<Envio> listar(int pagina, int tamano) {
            return envios.stream().skip((long) pagina * tamano).limit(tamano).toList();
        }

        @Override
        public boolean existeActivoDeVehiculo(Long idVehiculo) {
            return envios.stream().anyMatch(e -> e.idVehiculo().equals(idVehiculo)
                    && (e.estadoEnvio() == EstadoEnvio.PROGRAMADO || e.estadoEnvio() == EstadoEnvio.EN_RUTA));
        }
    }

    static class LiquidacionesEnMemoria implements LiquidacionRepositoryPort {
        final List<Liquidacion> liquidaciones = new ArrayList<>();

        @Override
        public Liquidacion guardarNueva(Liquidacion liquidacion) {
            Liquidacion guardada = liquidacion.conId((long) liquidaciones.size() + 1);
            liquidaciones.add(guardada);
            return guardada;
        }

        @Override
        public void actualizar(Liquidacion liquidacion) {
            liquidaciones.replaceAll(l -> l.idLiquidacion().equals(liquidacion.idLiquidacion()) ? liquidacion : l);
        }

        @Override
        public Optional<Liquidacion> buscarPorLote(Long idLote) {
            return liquidaciones.stream().filter(l -> l.idLote().equals(idLote)).findFirst();
        }

        @Override
        public Optional<Liquidacion> buscarPorId(Long idLiquidacion) {
            return liquidaciones.stream().filter(l -> l.idLiquidacion().equals(idLiquidacion)).findFirst();
        }

        @Override
        public List<Liquidacion> listar(int pagina, int tamano) {
            return liquidaciones.stream().skip((long) pagina * tamano).limit(tamano).toList();
        }
    }

    static class PagosEnMemoria implements PagoRepositoryPort {
        final List<Pago> pagos = new ArrayList<>();

        @Override
        public List<Pago> pendientesCreadosAntesDe(Instant fecha, int limite) {
            return pagos.stream().filter(p -> p.estadoPago() == EstadoPago.PENDIENTE && p.fechaCreacion().isBefore(fecha))
                    .limit(limite).toList();
        }

        @Override
        public Pago guardarNuevo(Pago pago) {
            Pago guardado = pago.conId((long) pagos.size() + 1);
            pagos.add(guardado);
            return guardado;
        }

        @Override
        public void actualizar(Pago pago) {
            for (int i = 0; i < pagos.size(); i++) {
                if (pagos.get(i).idPago().equals(pago.idPago())) {
                    pagos.set(i, pago);
                }
            }
        }

        @Override
        public Optional<Pago> buscarPorId(Long idPago) {
            return pagos.stream().filter(p -> p.idPago().equals(idPago)).findFirst();
        }

        @Override
        public Optional<Pago> buscarPorClave(String clave) {
            return pagos.stream().filter(p -> p.claveIdempotencia().equals(clave)).findFirst();
        }

        @Override
        public boolean existeAprobadoDe(Long idLiquidacion) {
            return pagos.stream().anyMatch(p -> p.idLiquidacion().equals(idLiquidacion)
                    && p.estadoPago() == EstadoPago.APROBADO);
        }

        @Override
        public boolean existePendienteDe(Long idLiquidacion) {
            return pagos.stream().anyMatch(p -> p.idLiquidacion().equals(idLiquidacion)
                    && p.estadoPago() == EstadoPago.PENDIENTE);
        }

        @Override
        public Optional<Pago> ultimoDeLiquidacion(Long idLiquidacion) {
            return pagos.stream().filter(p -> p.idLiquidacion().equals(idLiquidacion))
                    .reduce((primero, segundo) -> segundo);
        }

        @Override
        public List<Pago> listar(int pagina, int tamano) {
            return pagos.stream().skip((long) pagina * tamano).limit(tamano).toList();
        }
    }

    static class ComprobantesEnMemoria implements ComprobanteRepositoryPort {
        final List<Comprobante> comprobantes = new ArrayList<>();

        @Override
        public Comprobante guardarNuevo(Comprobante comprobante) {
            Comprobante guardado = comprobante.conId((long) comprobantes.size() + 1);
            comprobantes.add(guardado);
            return guardado;
        }

        @Override
        public void actualizar(Comprobante comprobante) {
            for (int i = 0; i < comprobantes.size(); i++) {
                if (comprobantes.get(i).idComprobante().equals(comprobante.idComprobante())) {
                    comprobantes.set(i, comprobante);
                }
            }
        }

        @Override
        public Optional<Comprobante> buscarPorId(Long idComprobante) {
            return comprobantes.stream().filter(c -> c.idComprobante().equals(idComprobante)).findFirst();
        }

        @Override
        public Optional<Comprobante> buscarPorPago(Long idPago) {
            return comprobantes.stream().filter(c -> c.idPago().equals(idPago)).findFirst();
        }

        @Override
        public List<Comprobante> pendientesParaReintento(Instant ahora, int limite) {
            return comprobantes.stream().filter(c -> c.esPendiente() && c.proximoIntento() != null
                    && !c.proximoIntento().isAfter(ahora)).limit(limite).toList();
        }
    }

    static class CorrelativoEnMemoria implements CorrelativoPort {
        final java.util.Map<String, Integer> ultimos = new java.util.HashMap<>();

        @Override
        public int siguiente(String serie) {
            return ultimos.merge(serie, 1, Integer::sum);
        }
    }

    static class PasarelaGrabadora implements PagoPort {
        final List<SolicitudCobro> solicitudes = new ArrayList<>();
        final java.util.Map<String, ResultadoCobro> cargos = new java.util.HashMap<>();
        java.util.function.Function<SolicitudCobro, ResultadoCobro> comportamiento = solicitud ->
                ResultadoCobro.aprobado("chr_test_abc123", "Visa", "4242", solicitud.metadatos());
        boolean noResponde = false;
        RuntimeException error = null;

        @Override
        public ResultadoCobro cobrar(SolicitudCobro solicitud) {
            solicitudes.add(solicitud);
            if (error != null) {
                throw error;
            }
            ResultadoCobro resultado = comportamiento.apply(solicitud);
            cargos.put(resultado.idCargo(), resultado);
            if (noResponde) {
                throw new ServicioExternoNoDisponibleException("sin respuesta");
            }
            return resultado;
        }

        @Override
        public Optional<ResultadoCobro> consultarCargo(String idCargo) {
            return Optional.ofNullable(cargos.get(idCargo));
        }
    }

    static class EmisorGrabador implements ComprobantePort {
        final List<DatosComprobante> emitidos = new ArrayList<>();
        boolean caido = false;
        EstadoComprobante respuesta = EstadoComprobante.ACEPTADO;

        @Override
        public ResultadoEmision emitir(DatosComprobante datos) {
            if (caido) {
                throw new ServicioExternoNoDisponibleException("proveedor caído");
            }
            emitidos.add(datos);
            return new ResultadoEmision(respuesta, "https://pdf/" + datos.serie() + "-" + datos.numero(), null, "ok");
        }

        @Override
        public ResultadoEmision consultar(TipoComprobante tipo, String serie, int numero) {
            if (caido) {
                throw new ServicioExternoNoDisponibleException("proveedor caído");
            }
            return new ResultadoEmision(respuesta, null, null, "consultado");
        }
    }

    static class EjecutorDirecto implements EjecutorPorNegocioPort {
        final List<String> negocios = new ArrayList<>();

        @Override
        public <T> T ejecutarEn(String identificadorNegocio, Supplier<T> accion) {
            negocios.add(identificadorNegocio);
            return accion.get();
        }
    }

    static class GeneradorTokenFalso implements GeneradorTokenPort {
        private int contador = 0;

        @Override
        public String nuevoToken() {
            contador++;
            return "token-" + contador;
        }

        @Override
        public String hash(String token) {
            return "hash:" + token;
        }
    }

    static class EnlacesEnMemoria implements EnlaceEnvioRepositoryPort {
        final List<EnlaceEnvio> enlaces = new ArrayList<>();
        final List<String> desactivados = new ArrayList<>();

        @Override
        public void guardar(EnlaceEnvio enlace) {
            enlaces.add(enlace);
        }

        @Override
        public void desactivarDeEnvio(String identificadorNegocio, Long idEnvio, TipoEnlace tipo) {
            enlaces.stream().filter(e -> e.identificadorNegocio().equals(identificadorNegocio)
                    && e.idEnvio().equals(idEnvio) && e.tipoEnlace() == tipo)
                    .forEach(e -> desactivados.add(e.hashToken()));
        }

        @Override
        public Optional<EnlaceEnvio> buscarVigentePorHash(String hashToken, Instant ahora) {
            return enlaces.stream()
                    .filter(e -> e.hashToken().equals(hashToken) && !desactivados.contains(hashToken)
                            && e.fechaExpiracion().isAfter(ahora))
                    .findFirst();
        }
    }

    static class SeguimientoEnMemoria implements SeguimientoRepositoryPort {
        final List<Posicion> posiciones = new ArrayList<>();
        final List<AlertaEnvio> alertas = new ArrayList<>();
        final List<Long> consentimientos = new ArrayList<>();

        @Override
        public Optional<Posicion> guardarPosicion(Posicion posicion) {
            boolean existe = posiciones.stream().anyMatch(p -> p.idEnvio().equals(posicion.idEnvio())
                    && p.fechaPosicion().equals(posicion.fechaPosicion()));
            if (existe) {
                return Optional.empty();
            }
            Posicion guardada = posicion.conId((long) posiciones.size() + 1);
            posiciones.add(guardada);
            return Optional.of(guardada);
        }

        @Override
        public List<Posicion> posicionesDesde(Long idEnvio, Instant desde) {
            return posiciones.stream().filter(p -> p.idEnvio().equals(idEnvio) && !p.fechaPosicion().isBefore(desde))
                    .sorted(java.util.Comparator.comparing(Posicion::fechaPosicion)).toList();
        }

        @Override
        public List<Posicion> ultimasPosiciones(Long idEnvio, int cantidad) {
            List<Posicion> todas = posiciones.stream().filter(p -> p.idEnvio().equals(idEnvio))
                    .sorted(java.util.Comparator.comparing(Posicion::fechaPosicion)).toList();
            return todas.subList(Math.max(0, todas.size() - cantidad), todas.size());
        }

        @Override
        public AlertaEnvio guardarAlerta(AlertaEnvio alerta) {
            AlertaEnvio guardada = alerta.conId((long) alertas.size() + 1);
            alertas.add(guardada);
            return guardada;
        }

        @Override
        public boolean existeAlertaDesde(Long idEnvio, TipoAlerta tipoAlerta, Instant desde) {
            return alertas.stream().anyMatch(a -> a.idEnvio().equals(idEnvio) && a.tipoAlerta() == tipoAlerta
                    && !a.fechaAlerta().isBefore(desde));
        }

        @Override
        public List<AlertaEnvio> alertas(Long idEnvio) {
            return alertas.stream().filter(a -> a.idEnvio().equals(idEnvio)).toList();
        }

        @Override
        public void registrarConsentimiento(Long idEnvio, Instant fecha) {
            if (!consentimientos.contains(idEnvio)) {
                consentimientos.add(idEnvio);
            }
        }

        @Override
        public boolean tieneConsentimiento(Long idEnvio) {
            return consentimientos.contains(idEnvio);
        }
    }

    static class PublicadorGrabador implements PublicadorSeguimientoPort {
        final List<Posicion> posiciones = new ArrayList<>();
        final List<Long> etas = new ArrayList<>();
        final List<AlertaEnvio> alertas = new ArrayList<>();
        final List<String> negocios = new ArrayList<>();

        @Override
        public void publicarPosicion(String identificadorNegocio, Long idEnvio, Posicion posicion, Long etaMinutos) {
            negocios.add(identificadorNegocio);
            posiciones.add(posicion);
            etas.add(etaMinutos);
        }

        @Override
        public void publicarAlerta(String identificadorNegocio, Long idEnvio, AlertaEnvio alerta) {
            negocios.add(identificadorNegocio);
            alertas.add(alerta);
        }
    }

    static class TransaccionDirecta implements TransaccionPort {
        int ejecuciones = 0;

        @Override
        public <T> T ejecutar(Supplier<T> accion) {
            ejecuciones++;
            return accion.get();
        }
    }

    static class HashFalso implements HashClavePort {
        @Override
        public String cifrar(String clave) {
            return "hash:" + clave;
        }

        @Override
        public boolean coincide(String clave, String hashClave) {
            return hashClave.equals("hash:" + clave);
        }
    }
}
