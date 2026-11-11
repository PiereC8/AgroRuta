package pe.agroruta.application.service;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.PoliticaClave;
import pe.agroruta.domain.port.in.RecuperarClaveUseCase;
import pe.agroruta.domain.port.out.EnvioCorreoPort;
import pe.agroruta.domain.port.out.GeneradorTokenPort;
import pe.agroruta.domain.port.out.HashClavePort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;
import pe.agroruta.domain.port.out.RecuperacionClavePort;
import pe.agroruta.domain.port.out.RecuperacionClavePort.Cuenta;

public class RecuperarClaveService implements RecuperarClaveUseCase {

    private static final Logger REGISTRO = LoggerFactory.getLogger(RecuperarClaveService.class);

    public static final Duration VIGENCIA = Duration.ofMinutes(30);
    public static final int MAXIMO_SOLICITUDES_POR_HORA = 3;
    static final String ENLACE_INVALIDO = "El enlace no es válido o ya venció. Pide uno nuevo.";

    private final NegocioRepositoryPort negocios;
    private final RecuperacionClavePort recuperaciones;
    private final GeneradorTokenPort generador;
    private final HashClavePort hashes;
    private final EnvioCorreoPort correo;
    private final Clock reloj;

    public RecuperarClaveService(NegocioRepositoryPort negocios, RecuperacionClavePort recuperaciones,
                                 GeneradorTokenPort generador, HashClavePort hashes, EnvioCorreoPort correo,
                                 Clock reloj) {
        this.negocios = negocios;
        this.recuperaciones = recuperaciones;
        this.generador = generador;
        this.hashes = hashes;
        this.correo = correo;
        this.reloj = reloj;
    }

    @Override
    public void solicitar(String identificadorNegocio, String nombreUsuario, String urlBase) {
        if (vacio(identificadorNegocio) || vacio(nombreUsuario)
                || negocios.buscarActivoPorIdentificador(identificadorNegocio).isEmpty()) {
            return;
        }
        Optional<Cuenta> encontrada = recuperaciones.buscarCuenta(identificadorNegocio, nombreUsuario.strip());
        if (encontrada.isEmpty() || !encontrada.get().activo() || encontrada.get().correo() == null) {
            return;
        }
        Cuenta cuenta = encontrada.get();
        Instant ahora = reloj.instant();
        if (recuperaciones.solicitudesDesde(identificadorNegocio, cuenta.idUsuario(), ahora.minus(Duration.ofHours(1)))
                >= MAXIMO_SOLICITUDES_POR_HORA) {
            return;
        }
        String token = generador.nuevoToken();
        recuperaciones.guardarSolicitud(identificadorNegocio, cuenta.idUsuario(), generador.hash(token), ahora,
                ahora.plus(VIGENCIA));
        // El token va después de "#": el navegador no lo manda al servidor ni lo deja en los registros.
        String enlace = urlBase + "/recuperar.html?negocio="
                + URLEncoder.encode(identificadorNegocio, StandardCharsets.UTF_8) + "#token=" + token;
        try {
            correo.enviar(cuenta.correo(), "AgroRuta: cambia tu contraseña",
                    "Hola, " + cuenta.nombreUsuario() + ".\n\n"
                            + "Pediste cambiar tu contraseña de AgroRuta (negocio " + identificadorNegocio + "). "
                            + "Abre este enlace para poner una nueva:\n\n" + enlace + "\n\n"
                            + "El enlace sirve una sola vez y vence en " + VIGENCIA.toMinutes() + " minutos.\n"
                            + "Si no lo pediste tú, ignora este correo: tu contraseña no cambia.");
        } catch (RuntimeException e) {
            // No se le dice a quien lo pidió: la respuesta debe ser la misma exista o no la cuenta.
            REGISTRO.warn("No se pudo enviar el correo para recuperar la clave en {}: {}", identificadorNegocio,
                    e.getMessage());
        }
    }

    @Override
    public void restablecer(String identificadorNegocio, String token, String claveNueva) {
        PoliticaClave.validar(claveNueva);
        if (vacio(identificadorNegocio) || vacio(token)
                || negocios.buscarActivoPorIdentificador(identificadorNegocio).isEmpty()) {
            throw new DominioException(ENLACE_INVALIDO);
        }
        Long idUsuario = recuperaciones.consumirToken(identificadorNegocio, generador.hash(token.strip()),
                reloj.instant()).orElseThrow(() -> new DominioException(ENLACE_INVALIDO));
        recuperaciones.cambiarClave(identificadorNegocio, idUsuario, hashes.cifrar(claveNueva));
    }

    private static boolean vacio(String texto) {
        return texto == null || texto.isBlank();
    }
}
