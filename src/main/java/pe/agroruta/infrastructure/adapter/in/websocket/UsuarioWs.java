package pe.agroruta.infrastructure.adapter.in.websocket;

import java.security.Principal;

import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoEnlace;

/**
 * Quién está conectado por WebSocket: un usuario del sistema (con su JWT) o alguien con un enlace de token.
 * El nombre es único por envío y papel, que es lo que usa Spring para dirigir mensajes privados.
 */
public record UsuarioWs(String nombre, String identificadorNegocio, Rol rolUsuario, SesionEnlace sesionEnlace)
        implements Principal {

    public static UsuarioWs interno(String nombreUsuario, String identificadorNegocio, Rol rolUsuario) {
        return new UsuarioWs("usuario:" + identificadorNegocio + ":" + nombreUsuario, identificadorNegocio,
                rolUsuario, null);
    }

    public static UsuarioWs deEnlace(SesionEnlace sesion) {
        return new UsuarioWs("enlace:" + sesion.tipoEnlace() + ":" + sesion.identificadorNegocio() + ":"
                + sesion.idEnvio(), sesion.identificadorNegocio(), null, sesion);
    }

    public boolean esInterno() {
        return sesionEnlace == null;
    }

    public boolean esConductor() {
        return sesionEnlace != null && sesionEnlace.tipoEnlace() == TipoEnlace.CONDUCTOR;
    }

    @Override
    public String getName() {
        return nombre;
    }
}
