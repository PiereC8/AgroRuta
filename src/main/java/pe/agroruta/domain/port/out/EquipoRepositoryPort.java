package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import pe.agroruta.domain.model.MiembroEquipo;
import pe.agroruta.domain.model.Rol;

/** Las personas del negocio en curso (el que fija el contexto de la petición). */
public interface EquipoRepositoryPort {

    List<MiembroEquipo> listar();

    Optional<MiembroEquipo> buscar(Long idUsuario);

    boolean existePorNombre(String nombreUsuario);

    MiembroEquipo crearOperador(String nombreUsuario, String hashClave, String correo);

    void actualizarCorreo(Long idUsuario, String correo, boolean avisosActivos);

    void actualizarActivo(Long idUsuario, boolean activo);

    /** Quienes pueden recibir un aviso: activos, con avisos encendidos y con correo, de alguno de los roles. */
    List<MiembroEquipo> destinatarios(Set<Rol> roles);
}
