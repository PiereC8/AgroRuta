package pe.agroruta.domain.model;

/** Una persona del negocio y cómo avisarle. El correo es opcional; sin él no recibe avisos. */
public record MiembroEquipo(Long idUsuario, String nombreUsuario, Rol rol, String correo, boolean avisosActivos,
                            boolean activo) {

    public boolean puedeRecibirAvisos() {
        return activo && avisosActivos && correo != null;
    }
}
