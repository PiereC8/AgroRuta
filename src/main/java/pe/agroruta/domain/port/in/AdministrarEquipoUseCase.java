package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.MiembroEquipo;

public interface AdministrarEquipoUseCase {

    List<MiembroEquipo> listar();

    MiembroEquipo crearOperador(String nombreUsuario, String clave, String correo);

    void cambiarCorreo(Long idUsuario, String correo, boolean avisosActivos);

    /** Nadie puede desactivarse a sí mismo: el negocio se quedaría sin quien lo administre. */
    void cambiarActivo(Long idUsuario, boolean activo, String solicitante);
}
