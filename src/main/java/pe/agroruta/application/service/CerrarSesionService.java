package pe.agroruta.application.service;

import pe.agroruta.domain.model.ReglasSesion;
import pe.agroruta.domain.port.in.CerrarSesionUseCase;
import pe.agroruta.domain.port.out.GeneradorTokenPort;
import pe.agroruta.domain.port.out.SesionUsuarioPort;

/**
 * RFF-01 (sesión única): cierra la sesión del token para que la cuenta quede libre al instante.
 * Cómo: solo borra la sesión si es la misma del token (no cierra una más nueva de otro dispositivo).
 */
public class CerrarSesionService implements CerrarSesionUseCase {

    private final SesionUsuarioPort sesiones;
    private final GeneradorTokenPort generador;

    public CerrarSesionService(SesionUsuarioPort sesiones, GeneradorTokenPort generador) {
        this.sesiones = sesiones;
        this.generador = generador;
    }

    @Override
    public void cerrar(String identificadorNegocio, String nombreUsuario, String sesion) {
        if (ReglasSesion.aplicaA(identificadorNegocio) && sesion != null && !sesion.isBlank()) {
            sesiones.cerrar(identificadorNegocio, nombreUsuario, generador.hash(sesion));
        }
    }
}
