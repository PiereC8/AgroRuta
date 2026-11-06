package pe.agroruta.infrastructure.adapter.in.web;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.IniciarSesionUseCase;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final IniciarSesionUseCase iniciarSesion;

    public AuthController(IniciarSesionUseCase iniciarSesion) {
        this.iniciarSesion = iniciarSesion;
    }

    @PostMapping("/login")
    public LoginRespuesta iniciar(@Valid @RequestBody LoginSolicitud solicitud) {
        String identificadorNegocio = solicitud.identificadorNegocio() == null
                || solicitud.identificadorNegocio().isBlank() ? null : solicitud.identificadorNegocio();
        var resultado = iniciarSesion.iniciar(new IniciarSesionUseCase.Comando(
                identificadorNegocio, solicitud.nombreUsuario(), solicitud.clave()));
        return new LoginRespuesta(resultado.token(), resultado.rolUsuario(), resultado.identificadorNegocio());
    }

    public record LoginSolicitud(String identificadorNegocio, @NotBlank String nombreUsuario,
                                 @NotBlank String clave) {
    }

    public record LoginRespuesta(String token, Rol rolUsuario, String identificadorNegocio) {
    }
}
