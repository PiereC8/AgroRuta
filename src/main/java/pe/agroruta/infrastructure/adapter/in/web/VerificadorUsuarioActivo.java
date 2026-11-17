package pe.agroruta.infrastructure.adapter.in.web;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import pe.agroruta.domain.model.ReglasSesion;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.out.GeneradorTokenPort;
import pe.agroruta.domain.port.out.SesionUsuarioPort;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;

/**
 * Revisa en la base que el usuario del token siga activo: un usuario desactivado pierde el acceso aunque su token
 * todavía no venza. La respuesta se guarda unos segundos para no consultar la base en cada petición.
 * RFF-01 (sesión única): además revisa que la sesión del token siga siendo la de la cuenta y anota su uso; si pasó
 * el tiempo de inactividad u otra sesión la reemplazó, el token deja de valer.
 */
public class VerificadorUsuarioActivo {

    static final Duration VIGENCIA = Duration.ofSeconds(30);

    private record Respuesta(boolean activo, Instant hasta) {
    }

    private final UsuarioRepositoryPort usuarios;
    /** Pueden ser null (pruebas antiguas): entonces solo se revisa que el usuario esté activo. */
    private final SesionUsuarioPort sesiones;
    private final GeneradorTokenPort generador;
    private final Clock reloj;
    private final Map<String, Respuesta> recientes = new ConcurrentHashMap<>();

    public VerificadorUsuarioActivo(UsuarioRepositoryPort usuarios, Clock reloj) {
        this(usuarios, null, null, reloj);
    }

    public VerificadorUsuarioActivo(UsuarioRepositoryPort usuarios, SesionUsuarioPort sesiones,
                                    GeneradorTokenPort generador, Clock reloj) {
        this.usuarios = usuarios;
        // Sin generador no se puede calcular la huella: se trabaja sin sesión única.
        this.sesiones = generador == null ? null : sesiones;
        this.generador = generador;
        this.reloj = reloj;
    }

    /** El administrador de la plataforma no pertenece a un negocio: su cuenta sale de la configuración. */
    public boolean estaActivo(String identificadorNegocio, String nombreUsuario) {
        return esValido(identificadorNegocio, nombreUsuario, null);
    }

    /** Usuario activo y, con sesión única, la sesión del token sigue abierta (y se anota su uso). */
    public boolean esValido(String identificadorNegocio, String nombreUsuario, String sesion) {
        if (identificadorNegocio == null) {
            return true;
        }
        boolean conSesion = sesiones != null && ReglasSesion.aplicaA(identificadorNegocio);
        if (conSesion && (sesion == null || sesion.isBlank())) {
            return false;
        }
        String clave = identificadorNegocio + "|" + nombreUsuario + "|" + (conSesion ? sesion : "");
        Instant ahora = reloj.instant();
        Respuesta guardada = recientes.get(clave);
        if (guardada != null && guardada.hasta().isAfter(ahora)) {
            return guardada.activo();
        }
        // Con sesión, el mismo UPDATE revisa que el usuario siga activo y anota el uso (como mucho cada 30 s).
        boolean valido = conSesion
                ? sesiones.usar(identificadorNegocio, nombreUsuario, generador.hash(sesion), ahora,
                        ReglasSesion.libreAntesDe(ahora))
                : usuarios.buscarPorNombre(identificadorNegocio, nombreUsuario).map(Usuario::usuarioActivo).orElse(false);
        recientes.put(clave, new Respuesta(valido, ahora.plus(VIGENCIA)));
        return valido;
    }
}
