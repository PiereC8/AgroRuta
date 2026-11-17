package pe.agroruta.domain.port.out;

import pe.agroruta.domain.model.Rol;

public interface EmisorTokenPort {

    String emitir(String identificadorNegocio, String nombreUsuario, Rol rolUsuario);

    /** RFF-01: el token lleva la sesión abierta, para revisar en cada petición que siga siendo la de la cuenta. */
    default String emitir(String identificadorNegocio, String nombreUsuario, Rol rolUsuario, String sesion) {
        return emitir(identificadorNegocio, nombreUsuario, rolUsuario);
    }
}
