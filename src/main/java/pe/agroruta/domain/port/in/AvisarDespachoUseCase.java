package pe.agroruta.domain.port.in;

import java.time.Instant;

/**
 * Al despachar, deja listo el enlace del conductor (para mandarlo por WhatsApp) y le manda por correo al comprador el
 * enlace para seguir su pedido, si tiene correo. Así el operador no copia enlaces a mano.
 */
public interface AvisarDespachoUseCase {

    Resultado avisar(String identificadorNegocio, Long idEnvio, String urlBase);

    /** telefonoTransportista y correoComprador vienen en null si no están registrados. */
    record Resultado(String urlConductor, String nombreTransportista, String telefonoTransportista,
                     String correoComprador, Instant fechaExpiracion) {
    }
}
