package pe.agroruta.application.service;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.out.HashClavePort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;
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
