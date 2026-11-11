package pe.agroruta.infrastructure.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import pe.agroruta.domain.port.in.CambiarClaveUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

/** La cuenta de quien tiene la sesión: por ahora, cambiar su propia contraseña. */
@RestController
public class CuentaController {

    private final CambiarClaveUseCase cambiarClave;

    public CuentaController(CambiarClaveUseCase cambiarClave) {
        this.cambiarClave = cambiarClave;
    }

    @PutMapping("/api/cuenta/clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarClave(@Valid @RequestBody CambioClave solicitud, @AuthenticationPrincipal DatosToken usuario) {
        cambiarClave.cambiar(usuario.identificadorNegocio(), usuario.nombreUsuario(), solicitud.claveActual(),
                solicitud.claveNueva());
    }

    public record CambioClave(@NotBlank String claveActual, @NotBlank String claveNueva) {
    }
}
