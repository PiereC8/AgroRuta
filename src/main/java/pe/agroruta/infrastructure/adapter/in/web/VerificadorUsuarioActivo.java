package pe.agroruta.infrastructure.adapter.in.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;

/**
 * Revisa en la base que el usuario del token siga activo: un usuario desactivado pierde el acceso aunque su token
 * todavía no venza. La respuesta se guarda unos segundos para no consultar la base en cada petición.
 */
public class VerificadorUsuarioActivo {

    static final Duration VIGENCIA = Duration.ofSeconds(30);

    private record Respuesta(boolean activo, Instant hasta) {
    }

    private final UsuarioRepositoryPort usuarios;
    private final Clock reloj;
    private final Map<String, Respuesta> recientes = new ConcurrentHashMap<>();

    public VerificadorUsuarioActivo(UsuarioRepositoryPort usuarios, Clock reloj) {
        this.usuarios = usuarios;
        this.reloj = reloj;
    }

    /** El administrador de la plataforma no pertenece a un negocio: su cuenta sale de la configuración. */
    public boolean estaActivo(String identificadorNegocio, String nombreUsuario) {
        if (identificadorNegocio == null) {
            return true;
        }
        String clave = identificadorNegocio + "|" + nombreUsuario;
        Instant ahora = reloj.instant();
        Respuesta guardada = recientes.get(clave);
        if (guardada != null && guardada.hasta().isAfter(ahora)) {
            return guardada.activo();
        }
        boolean activo = usuarios.buscarPorNombre(identificadorNegocio, nombreUsuario)
                .map(Usuario::usuarioActivo).orElse(false);
        recientes.put(clave, new Respuesta(activo, ahora.plus(VIGENCIA)));
        return activo;
    }
}
