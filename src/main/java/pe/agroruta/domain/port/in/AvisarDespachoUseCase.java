package pe.agroruta.domain.port.in;

import java.time.Instant;

/**
 * Puerto de entrada de RF-14 (enlace del comprador por correo) y RF-21 (enlace del conductor por WhatsApp).
 * Requerimientos finales: RFF-09 (enlaces de seguimiento) y RFF-22 (avisos por correo).
 * <p>
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
