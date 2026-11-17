package pe.agroruta.domain.port.out;

import java.time.Instant;
import java.util.Optional;

import pe.agroruta.domain.model.Rol;

/** Los códigos de la verificación en dos pasos, en la base del negocio indicado. Solo guarda hashes. */
public interface VerificacionSesionPort {

    /** Un inicio de sesión que espera su código, con los datos del usuario que lo pidió. */
    record Desafio(Long idVerificacion, Long idUsuario, String nombreUsuario, Rol rolUsuario, boolean usuarioActivo,
                   String hashCodigo, Instant fechaVencimiento, Instant ultimoEnvio, int envios,
                   int intentosFallidos, boolean usado) {
    }

    Optional<String> correoDe(String identificadorNegocio, Long idUsuario);

    /** Cuántos códigos se le enviaron al usuario desde ese momento (contando los reenvíos). */
    int codigosDesde(String identificadorNegocio, Long idUsuario, Instant desde);

    /** Guarda el desafío nuevo y anula los que el usuario tenía sin usar. */
    void crear(String identificadorNegocio, Long idUsuario, String hashDesafio, String hashCodigo, Instant ahora,
               Instant vence);

    Optional<Desafio> buscar(String identificadorNegocio, String hashDesafio);

    void sumarIntentoFallido(String identificadorNegocio, Long idVerificacion);

    /** Un código nuevo para el mismo desafío: vuelve a contar los intentos y la vigencia. */
    void reemplazarCodigo(String identificadorNegocio, Long idVerificacion, String hashCodigo, Instant ahora,
                          Instant vence);

    /** Marca el desafío como usado si sigue vigente; si dos peticiones lo usan a la vez, solo una gana. */
    boolean consumir(String identificadorNegocio, Long idVerificacion, Instant ahora);
}
