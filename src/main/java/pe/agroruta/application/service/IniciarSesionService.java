package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.agroruta.domain.model.CredencialesInvalidasException;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.ReglasVerificacion;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.in.IniciarSesionUseCase;
import pe.agroruta.domain.port.out.EmisorTokenPort;
import pe.agroruta.domain.port.out.EnvioCorreoPort;
import pe.agroruta.domain.port.out.GeneradorTokenPort;
import pe.agroruta.domain.port.out.HashClavePort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;
import pe.agroruta.domain.port.out.VerificacionSesionPort;

/**
 * RFF-01: iniciar sesión con negocio, usuario y contraseña.
 * Cómo: revisa la clave (con bloqueo tras 5 fallos) y emite el token. Si es el Dueño y tiene correo, todavía no
 * emite el token: le manda un código de 6 dígitos y devuelve un desafío (verificación en dos pasos).
 */
public class IniciarSesionService implements IniciarSesionUseCase {

    private static final Logger REGISTRO = LoggerFactory.getLogger(IniciarSesionService.class);

    private final UsuarioRepositoryPort usuarios;
    private final NegocioRepositoryPort negocios;
    private final HashClavePort hashes;
    private final EmisorTokenPort emisor;
    private final VerificacionSesionPort verificaciones;
    private final GeneradorTokenPort generador;
    private final EnvioCorreoPort correo;
    private final Clock reloj;

    public IniciarSesionService(UsuarioRepositoryPort usuarios, NegocioRepositoryPort negocios,
                                HashClavePort hashes, EmisorTokenPort emisor, VerificacionSesionPort verificaciones,
                                GeneradorTokenPort generador, EnvioCorreoPort correo, Clock reloj) {
        this.usuarios = usuarios;
        this.negocios = negocios;
        this.hashes = hashes;
        this.emisor = emisor;
        this.verificaciones = verificaciones;
        this.generador = generador;
        this.correo = correo;
        this.reloj = reloj;
    }

    @Override
    public Resultado iniciar(Comando comando) {
        String identificadorNegocio = comando.identificadorNegocio();
        if (identificadorNegocio != null && negocios.buscarActivoPorIdentificador(identificadorNegocio).isEmpty()) {
            throw new CredencialesInvalidasException();
        }

        Usuario usuario = usuarios.buscarPorNombre(identificadorNegocio, comando.nombreUsuario())
                .orElseThrow(CredencialesInvalidasException::new);

        Instant ahora = reloj.instant();
        if (!usuario.usuarioActivo() || usuario.estaBloqueado(ahora)) {
            throw new CredencialesInvalidasException();
        }
        if (comando.clave() == null || !hashes.coincide(comando.clave(), usuario.hashClave())) {
            usuarios.guardar(identificadorNegocio, usuario.conIntentoFallido(ahora));
            throw new CredencialesInvalidasException();
        }

        if (usuario.intentosFallidosSesion() > 0 || usuario.fechaBloqueoHasta() != null) {
            usuarios.guardar(identificadorNegocio, usuario.conAccesoExitoso());
        }

        // Segundo paso, solo para el Dueño: el token se entrega después, con el código correcto.
        if (ReglasVerificacion.aplicaA(usuario.rolUsuario(), identificadorNegocio)) {
            Optional<String> correoDueno = verificaciones.correoDe(identificadorNegocio, usuario.idUsuario());
            if (correoDueno.isPresent()) {
                return enviarCodigo(identificadorNegocio, usuario, correoDueno.get(), ahora);
            }
            // Sin correo entra con su clave, y la pantalla le pide registrarlo para activar el segundo paso.
            String token = emisor.emitir(identificadorNegocio, usuario.nombreUsuario(), usuario.rolUsuario());
            return new Resultado(token, usuario.rolUsuario(), identificadorNegocio, null, null, true);
        }

        String token = emisor.emitir(identificadorNegocio, usuario.nombreUsuario(), usuario.rolUsuario());
        return new Resultado(token, usuario.rolUsuario(), identificadorNegocio);
    }

    private Resultado enviarCodigo(String identificadorNegocio, Usuario usuario, String correoDueno, Instant ahora) {
        if (verificaciones.codigosDesde(identificadorNegocio, usuario.idUsuario(), ahora.minus(Duration.ofHours(1)))
                >= ReglasVerificacion.MAXIMO_CODIGOS_POR_HORA) {
            throw new DominioException(ReglasVerificacion.DEMASIADOS_CODIGOS);
        }
        String desafio = generador.nuevoToken();
        String codigo = generador.nuevoCodigo();
        verificaciones.crear(identificadorNegocio, usuario.idUsuario(), generador.hash(desafio),
                generador.hash(ReglasVerificacion.textoAHashear(desafio, codigo)), ahora,
                ahora.plus(ReglasVerificacion.VIGENCIA));
        // Se envía en el momento (no por la bandeja de salida, que espera hasta un minuto).
        try {
            correo.enviar(correoDueno, ReglasVerificacion.asunto(codigo),
                    ReglasVerificacion.cuerpo(usuario.nombreUsuario(), identificadorNegocio, codigo));
        } catch (RuntimeException e) {
            REGISTRO.warn("No se pudo enviar el código de verificación en {}: {}", identificadorNegocio,
                    e.getMessage());
            throw new DominioException("No pudimos enviar el código a tu correo. Intenta de nuevo en unos minutos.");
        }
        return Resultado.esperaCodigo(usuario.rolUsuario(), identificadorNegocio, desafio,
                ReglasVerificacion.ocultarCorreo(correoDueno));
    }
}
