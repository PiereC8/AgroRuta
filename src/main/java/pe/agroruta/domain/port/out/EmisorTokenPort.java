package pe.agroruta.domain.port.out;

import pe.agroruta.domain.model.Rol;

public interface EmisorTokenPort {

    String emitir(String identificadorNegocio, String nombreUsuario, Rol rolUsuario);
}
