package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.Rol;

public interface IniciarSesionUseCase {

    Resultado iniciar(Comando comando);

    record Comando(String identificadorNegocio, String nombreUsuario, String clave) {
    }

    /**
     * Con {@code token}: la sesión ya empezó. Con {@code desafio}: falta el código que le llegó al Dueño a
     * {@code correoOculto} (verificación en dos pasos). {@code sinCorreo}: el Dueño entró, pero sin correo
     * registrado no tiene la verificación en dos pasos.
     */
    record Resultado(String token, Rol rolUsuario, String identificadorNegocio, String desafio, String correoOculto,
                     boolean sinCorreo) {

        public Resultado(String token, Rol rolUsuario, String identificadorNegocio) {
            this(token, rolUsuario, identificadorNegocio, null, null, false);
        }

        public static Resultado esperaCodigo(Rol rolUsuario, String identificadorNegocio, String desafio,
                                             String correoOculto) {
            return new Resultado(null, rolUsuario, identificadorNegocio, desafio, correoOculto, false);
        }

        public boolean requiereCodigo() {
            return desafio != null;
        }
    }
}
