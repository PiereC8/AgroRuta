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
import pe.agroruta.domain.port.in.VerificarCodigoUseCase;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    static final String SOLICITUD_RECIBIDA = "Si el negocio y el usuario existen y tienen un correo registrado, "
            + "te enviamos un enlace para cambiar la contraseña. Revisa tu correo (también la carpeta de spam).";

    private final IniciarSesionUseCase iniciarSesion;
    private final RecuperarClaveUseCase recuperarClave;
    private final VerificarCodigoUseCase verificarCodigo;
    private final String urlPublica;

    public AuthController(IniciarSesionUseCase iniciarSesion, RecuperarClaveUseCase recuperarClave,
                          VerificarCodigoUseCase verificarCodigo,
                          @Value("${agroruta.web.url-publica:}") String urlPublica) {
        this.iniciarSesion = iniciarSesion;
        this.recuperarClave = recuperarClave;
        this.verificarCodigo = verificarCodigo;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    @PostMapping("/login")
    public LoginRespuesta iniciar(@Valid @RequestBody LoginSolicitud solicitud) {
        String identificadorNegocio = solicitud.identificadorNegocio() == null
                || solicitud.identificadorNegocio().isBlank() ? null : solicitud.identificadorNegocio();
        var resultado = iniciarSesion.iniciar(new IniciarSesionUseCase.Comando(
                identificadorNegocio, solicitud.nombreUsuario(), solicitud.clave()));
        return LoginRespuesta.de(resultado);
    }

    /** Segundo paso del Dueño: el código de 6 dígitos que le llegó al correo. Si es correcto, entrega el token. */
    @PostMapping("/verificar")
    public LoginRespuesta verificar(@Valid @RequestBody VerificarSolicitud solicitud) {
        return LoginRespuesta.de(verificarCodigo.verificar(solicitud.identificadorNegocio().strip(),
                solicitud.desafio(), solicitud.codigo()));
    }

    @PostMapping("/reenviar-codigo")
    public MensajeRespuesta reenviarCodigo(@Valid @RequestBody ReenviarSolicitud solicitud) {
        String correoOculto = verificarCodigo.reenviar(solicitud.identificadorNegocio().strip(), solicitud.desafio());
        return new MensajeRespuesta("Te enviamos un código nuevo a " + correoOculto + ".");
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

    /**
     * Con {@code requiereCodigo}: no hay token todavía; la pantalla pide el código y lo manda con {@code desafio}.
     * Con {@code avisoCorreo}: el Dueño entró sin correo registrado y se le pide registrarlo.
     */
    public record LoginRespuesta(String token, Rol rolUsuario, String identificadorNegocio, boolean requiereCodigo,
                                 String desafio, String correoOculto, boolean avisoCorreo) {
        static LoginRespuesta de(IniciarSesionUseCase.Resultado r) {
            return new LoginRespuesta(r.token(), r.rolUsuario(), r.identificadorNegocio(), r.requiereCodigo(),
                    r.desafio(), r.correoOculto(), r.sinCorreo());
        }
    }

    public record VerificarSolicitud(@NotBlank @Size(max = 60) String identificadorNegocio,
                                     @NotBlank @Size(max = 100) String desafio,
                                     @NotBlank @Size(max = 20) String codigo) {
    }

    public record ReenviarSolicitud(@NotBlank @Size(max = 60) String identificadorNegocio,
                                    @NotBlank @Size(max = 100) String desafio) {
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
