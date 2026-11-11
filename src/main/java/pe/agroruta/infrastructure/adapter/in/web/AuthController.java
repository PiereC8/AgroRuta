package pe.agroruta.infrastructure.adapter.in.web;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.IniciarSesionUseCase;
import pe.agroruta.domain.port.in.RecuperarClaveUseCase;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String SOLICITUD_RECIBIDA = "Si el negocio y el usuario existen y tienen un correo registrado, "
            + "te enviamos un enlace para cambiar la contraseña. Revisa tu correo (también la carpeta de spam).";

    private final IniciarSesionUseCase iniciarSesion;
    private final RecuperarClaveUseCase recuperarClave;
    private final String urlPublica;

    public AuthController(IniciarSesionUseCase iniciarSesion, RecuperarClaveUseCase recuperarClave,
                          @Value("${agroruta.web.url-publica:}") String urlPublica) {
        this.iniciarSesion = iniciarSesion;
        this.recuperarClave = recuperarClave;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    @PostMapping("/login")
    public LoginRespuesta iniciar(@Valid @RequestBody LoginSolicitud solicitud) {
        String identificadorNegocio = solicitud.identificadorNegocio() == null
                || solicitud.identificadorNegocio().isBlank() ? null : solicitud.identificadorNegocio();
        var resultado = iniciarSesion.iniciar(new IniciarSesionUseCase.Comando(
                identificadorNegocio, solicitud.nombreUsuario(), solicitud.clave()));
        return new LoginRespuesta(resultado.token(), resultado.rolUsuario(), resultado.identificadorNegocio());
    }

    /** Responde siempre lo mismo, exista o no la cuenta. */
    @PostMapping("/recuperar")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public MensajeRespuesta recuperar(@Valid @RequestBody RecuperarSolicitud solicitud) {
        recuperarClave.solicitar(solicitud.identificadorNegocio().strip(), solicitud.nombreUsuario().strip(),
                urlPublica.isBlank() ? ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() : urlPublica);
        return new MensajeRespuesta(SOLICITUD_RECIBIDA);
    }

    @PostMapping("/restablecer")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecer(@Valid @RequestBody RestablecerSolicitud solicitud) {
        recuperarClave.restablecer(solicitud.identificadorNegocio().strip(), solicitud.token(),
                solicitud.claveNueva());
    }

    public record LoginSolicitud(String identificadorNegocio, @NotBlank String nombreUsuario,
                                 @NotBlank String clave) {
    }

    public record LoginRespuesta(String token, Rol rolUsuario, String identificadorNegocio) {
    }

    public record RecuperarSolicitud(@NotBlank @Size(max = 60) String identificadorNegocio,
                                     @NotBlank @Size(max = 50) String nombreUsuario) {
    }

    public record RestablecerSolicitud(@NotBlank @Size(max = 60) String identificadorNegocio,
                                       @NotBlank @Size(max = 100) String token,
                                       @NotBlank @Size(max = 72) String claveNueva) {
    }

    public record MensajeRespuesta(String mensaje) {
    }
}
