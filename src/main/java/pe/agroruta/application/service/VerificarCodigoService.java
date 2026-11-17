package pe.agroruta.application.service;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.ReglasVerificacion;
import pe.agroruta.domain.port.in.IniciarSesionUseCase.Resultado;
import pe.agroruta.domain.port.in.VerificarCodigoUseCase;
import pe.agroruta.domain.port.out.EmisorTokenPort;
import pe.agroruta.domain.port.out.EnvioCorreoPort;
import pe.agroruta.domain.port.out.GeneradorTokenPort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;
import pe.agroruta.domain.port.out.VerificacionSesionPort;
import pe.agroruta.domain.port.out.VerificacionSesionPort.Desafio;

/**
 * RFF-01 (verificación en dos pasos): el Dueño escribe el código de 6 dígitos que le llegó al correo.
 * Cómo: busca el desafío por su hash, revisa vigencia e intentos, compara el hash del código y, si es correcto,
 * lo marca como usado y emite el token. También manda un código nuevo si el Dueño lo pide.
 */
public class VerificarCodigoService implements VerificarCodigoUseCase {

    private static final Logger REGISTRO = LoggerFactory.getLogger(VerificarCodigoService.class);

    private final NegocioRepositoryPort negocios;
    private final VerificacionSesionPort verificaciones;
    private final GeneradorTokenPort generador;
    private final EmisorTokenPort emisor;
    private final EnvioCorreoPort correo;
    private final Clock reloj;

    public VerificarCodigoService(NegocioRepositoryPort negocios, VerificacionSesionPort verificaciones,
                                  GeneradorTokenPort generador, EmisorTokenPort emisor, EnvioCorreoPort correo,
                                  Clock reloj) {
        this.negocios = negocios;
        this.verificaciones = verificaciones;
        this.generador = generador;
        this.emisor = emisor;
        this.correo = correo;
        this.reloj = reloj;
    }

    @Override
    public Resultado verificar(String identificadorNegocio, String desafio, String codigo) {
        String limpio = ReglasVerificacion.normalizarCodigo(codigo);
        Instant ahora = reloj.instant();
        Desafio pendiente = vigente(identificadorNegocio, desafio, ahora);

        String esperado = pendiente.hashCodigo();
        String recibido = generador.hash(ReglasVerificacion.textoAHashear(desafio, limpio));
        // Comparación en tiempo constante: no deja adivinar el código por lo que tarda la respuesta.
        if (!MessageDigest.isEqual(esperado.getBytes(StandardCharsets.UTF_8),
                recibido.getBytes(StandardCharsets.UTF_8))) {
            verificaciones.sumarIntentoFallido(identificadorNegocio, pendiente.idVerificacion());
            int quedan = ReglasVerificacion.MAXIMO_INTENTOS - pendiente.intentosFallidos() - 1;
            throw new DominioException(quedan > 0
                    ? "El código no es correcto. Te " + (quedan == 1 ? "queda 1 intento." : "quedan " + quedan + " intentos.")
                    : "El código no es correcto y ya no quedan intentos. Vuelve a iniciar sesión.");
        }
        if (!verificaciones.consumir(identificadorNegocio, pendiente.idVerificacion(), ahora)) {
            throw new DominioException(ReglasVerificacion.CODIGO_VENCIDO);
        }
        String token = emisor.emitir(identificadorNegocio, pendiente.nombreUsuario(), pendiente.rolUsuario());
        return new Resultado(token, pendiente.rolUsuario(), identificadorNegocio);
    }

    @Override
    public String reenviar(String identificadorNegocio, String desafio) {
        Instant ahora = reloj.instant();
        Desafio pendiente = vigente(identificadorNegocio, desafio, ahora);
        Instant puedeDesde = pendiente.ultimoEnvio().plus(ReglasVerificacion.ESPERA_REENVIO);
        if (ahora.isBefore(puedeDesde)) {
            long segundos = Math.max(1, Duration.between(ahora, puedeDesde).toSeconds());
            throw new DominioException("Espera " + segundos + " segundos para pedir otro código.");
        }
        if (pendiente.envios() > ReglasVerificacion.MAXIMO_REENVIOS
                || verificaciones.codigosDesde(identificadorNegocio, pendiente.idUsuario(),
                ahora.minus(Duration.ofHours(1))) >= ReglasVerificacion.MAXIMO_CODIGOS_POR_HORA) {
            throw new DominioException(ReglasVerificacion.DEMASIADOS_CODIGOS);
        }
        String correoDueno = verificaciones.correoDe(identificadorNegocio, pendiente.idUsuario())
                .orElseThrow(() -> new DominioException(ReglasVerificacion.CODIGO_VENCIDO));
        String codigo = generador.nuevoCodigo();
        verificaciones.reemplazarCodigo(identificadorNegocio, pendiente.idVerificacion(),
                generador.hash(ReglasVerificacion.textoAHashear(desafio, codigo)), ahora,
                ahora.plus(ReglasVerificacion.VIGENCIA));
        try {
            correo.enviar(correoDueno, ReglasVerificacion.asunto(codigo),
                    ReglasVerificacion.cuerpo(pendiente.nombreUsuario(), identificadorNegocio, codigo));
        } catch (RuntimeException e) {
            REGISTRO.warn("No se pudo reenviar el código de verificación en {}: {}", identificadorNegocio,
                    e.getMessage());
            throw new DominioException("No pudimos enviar el código a tu correo. Intenta de nuevo en unos minutos.");
        }
        return ReglasVerificacion.ocultarCorreo(correoDueno);
    }

    /** El desafío existe, no se usó, no venció, le quedan intentos y su usuario sigue activo. */
    private Desafio vigente(String identificadorNegocio, String desafio, Instant ahora) {
        if (identificadorNegocio == null || identificadorNegocio.isBlank() || desafio == null || desafio.isBlank()
                || negocios.buscarActivoPorIdentificador(identificadorNegocio).isEmpty()) {
            throw new DominioException(ReglasVerificacion.CODIGO_VENCIDO);
        }
        return verificaciones.buscar(identificadorNegocio, generador.hash(desafio))
                .filter(d -> !d.usado() && d.usuarioActivo() && ahora.isBefore(d.fechaVencimiento())
                        && d.intentosFallidos() < ReglasVerificacion.MAXIMO_INTENTOS
                        && ReglasVerificacion.aplicaA(d.rolUsuario(), identificadorNegocio))
                .orElseThrow(() -> new DominioException(ReglasVerificacion.CODIGO_VENCIDO));
    }
}
