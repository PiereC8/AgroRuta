package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.MiembroEquipo;

public interface AdministrarEquipoUseCase {

    List<MiembroEquipo> listar();

    MiembroEquipo crearOperador(String nombreUsuario, String clave, String correo);

    void cambiarCorreo(Long idUsuario, String correo, boolean avisosActivos);

    /** Nadie puede desactivarse a sí mismo: el negocio se quedaría sin quien lo administre. */
    void cambiarActivo(Long idUsuario, boolean activo, String solicitante);

    /**
     * El Dueño le pone una clave nueva a un Operador que la olvidó; también le quita el bloqueo por intentos fallidos.
     * La propia se cambia con la clave actual (CambiarClaveUseCase), no aquí.
     */
    void restablecerClave(Long idUsuario, String claveNueva, String solicitante);
}
