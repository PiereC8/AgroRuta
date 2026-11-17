package pe.agroruta.infrastructure.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import pe.agroruta.domain.port.in.CerrarSesionUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

/**
 * RFF-01 (sesión única). El latido mantiene abierta la sesión mientras la página está abierta (la revisión del token
 * anota el uso); «salir» la cierra para que la cuenta quede libre al instante.
 */
@RestController
@RequestMapping("/api/auth")
public class SesionController {

    private final CerrarSesionUseCase cerrarSesion;

    public SesionController(CerrarSesionUseCase cerrarSesion) {
        this.cerrarSesion = cerrarSesion;
    }

    /** No hace nada más: al pasar por FiltroJwt ya se anotó que la sesión sigue en uso. */
    @GetMapping("/sesion")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void latido() {
        // La revisión del token ya hizo el trabajo.
    }

    @PostMapping("/salir")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void salir(@AuthenticationPrincipal DatosToken usuario) {
        cerrarSesion.cerrar(usuario.identificadorNegocio(), usuario.nombreUsuario(), usuario.sesion());
    }
}
