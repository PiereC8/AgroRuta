package pe.agroruta.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.regex.Pattern;

public record Usuario(Long idUsuario, String nombreUsuario, String hashClave, Rol rolUsuario,
                      int intentosFallidosSesion, Instant fechaBloqueoHasta, boolean usuarioActivo) {

    public static final int MAXIMO_INTENTOS_FALLIDOS = 5;
    public static final Duration DURACION_BLOQUEO = Duration.ofMinutes(15);

    private static final Pattern FORMATO_NOMBRE = Pattern.compile("^[A-Za-z0-9_.-]{3,50}$");

    public Usuario {
        if (nombreUsuario == null || !FORMATO_NOMBRE.matcher(nombreUsuario).matches()) {
            throw new DominioException(
                    "El nombre de usuario debe tener de 3 a 50 caracteres: letras, números, punto, guion o guion bajo");
        }
        if (hashClave == null || hashClave.isBlank()) {
            throw new DominioException("La clave es obligatoria");
        }
        if (rolUsuario == null) {
            throw new DominioException("El rol es obligatorio");
        }
    }

    public static Usuario nuevo(String nombreUsuario, String hashClave, Rol rolUsuario) {
        return new Usuario(null, nombreUsuario, hashClave, rolUsuario, 0, null, true);
    }

    public boolean estaBloqueado(Instant ahora) {
        return fechaBloqueoHasta != null && ahora.isBefore(fechaBloqueoHasta);
    }

    public Usuario conIntentoFallido(Instant ahora) {
        int intentos = intentosFallidosSesion + 1;
        if (intentos >= MAXIMO_INTENTOS_FALLIDOS) {
            return new Usuario(idUsuario, nombreUsuario, hashClave, rolUsuario, 0,
                    ahora.plus(DURACION_BLOQUEO), usuarioActivo);
        }
        return new Usuario(idUsuario, nombreUsuario, hashClave, rolUsuario, intentos, fechaBloqueoHasta,
                usuarioActivo);
    }

    public Usuario conAccesoExitoso() {
        return new Usuario(idUsuario, nombreUsuario, hashClave, rolUsuario, 0, null, usuarioActivo);
    }

    public Usuario conId(Long nuevoIdUsuario) {
        return new Usuario(nuevoIdUsuario, nombreUsuario, hashClave, rolUsuario, intentosFallidosSesion,
                fechaBloqueoHasta, usuarioActivo);
    }
}
