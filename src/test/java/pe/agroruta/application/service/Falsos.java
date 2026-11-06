package pe.agroruta.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

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

/** Implementaciones en memoria de los puertos, para probar las reglas sin base de datos. */
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
            return pedidos.stream().anyMatch(p -> p.idLote().equals(idLote));
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
                    && e.estadoEnvio() != EstadoEnvio.ENTREGADO);
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
