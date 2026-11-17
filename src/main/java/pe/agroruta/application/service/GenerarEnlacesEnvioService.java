package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.EnlaceEnvio;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.GenerarEnlacesEnvioUseCase;
import pe.agroruta.domain.port.out.EnlaceEnvioRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.CifradoPort;
import pe.agroruta.domain.port.out.GeneradorTokenPort;

/**
 * RFF-09: enlaces del conductor y del comprador, con vencimiento de 30 días.
 * Cómo: el enlace se autentica por el hash del token; además se guarda cifrado para volver a mostrarlo con su QR
 * al abrir «Enlaces». Generar uno nuevo desactiva el anterior del mismo tipo.
 */
public class GenerarEnlacesEnvioService implements GenerarEnlacesEnvioUseCase {

    public static final Duration VIGENCIA = Duration.ofDays(30);

    private final EnvioRepositoryPort envios;
    private final EnlaceEnvioRepositoryPort enlaces;
    private final GeneradorTokenPort generador;
    /** Puede ser null (pruebas antiguas): entonces el token no se guarda y no se puede volver a mostrar. */
    private final CifradoPort cifrado;
    private final Clock reloj;

    public GenerarEnlacesEnvioService(EnvioRepositoryPort envios, EnlaceEnvioRepositoryPort enlaces,
                                      GeneradorTokenPort generador, CifradoPort cifrado, Clock reloj) {
        this.envios = envios;
        this.enlaces = enlaces;
        this.generador = generador;
        this.cifrado = cifrado;
        this.reloj = reloj;
    }

    public GenerarEnlacesEnvioService(EnvioRepositoryPort envios, EnlaceEnvioRepositoryPort enlaces,
                                      GeneradorTokenPort generador, Clock reloj) {
        this(envios, enlaces, generador, null, reloj);
    }

    @Override
    public EnlacesGenerados generar(String identificadorNegocio, Long idEnvio, TipoEnlace soloTipo) {
        Envio envio = envios.buscarPorId(idEnvio)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + idEnvio));
        // Un envío entregado ya no comparte ubicación, pero el comprador sí necesita su enlace para pagar.
        boolean entregado = envio.estadoEnvio() == EstadoEnvio.ENTREGADO;
        if (soloTipo == TipoEnlace.CONDUCTOR && entregado) {
            throw new ConflictoDominioException("El envío ya fue entregado: el conductor ya no necesita su enlace");
        }
        Instant expira = reloj.instant().plus(VIGENCIA);
        String tokenConductor = null;
        String tokenComprador = null;
        if (soloTipo != TipoEnlace.COMPRADOR && !entregado) {
            tokenConductor = renovar(identificadorNegocio, idEnvio, TipoEnlace.CONDUCTOR, expira);
        }
        if (soloTipo != TipoEnlace.CONDUCTOR) {
            tokenComprador = renovar(identificadorNegocio, idEnvio, TipoEnlace.COMPRADOR, expira);
        }
        return new EnlacesGenerados(tokenConductor, tokenComprador, expira);
    }

    /** Desactiva el enlace anterior de ese tipo y guarda uno nuevo: su hash (autentica) y el token cifrado. */
    private String renovar(String identificadorNegocio, Long idEnvio, TipoEnlace tipo, Instant expira) {
        String token = generador.nuevoToken();
        enlaces.desactivarDeEnvio(identificadorNegocio, idEnvio, tipo);
        enlaces.guardar(new EnlaceEnvio(identificadorNegocio, idEnvio, tipo, generador.hash(token), expira,
                cifrado == null ? null : cifrado.cifrar(token)));
        return token;
    }

    @Override
    public EnlacesVigentes vigentes(String identificadorNegocio, Long idEnvio) {
        Envio envio = envios.buscarPorId(idEnvio)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + idEnvio));
        Instant ahora = reloj.instant();
        // Entregado: el conductor ya no comparte ubicación, así que su enlace ya no se muestra.
        EnlaceEnvio conductor = envio.estadoEnvio() == EstadoEnvio.ENTREGADO ? null
                : enlaces.buscarVigenteDeEnvio(identificadorNegocio, idEnvio, TipoEnlace.CONDUCTOR, ahora).orElse(null);
        EnlaceEnvio comprador = enlaces.buscarVigenteDeEnvio(identificadorNegocio, idEnvio, TipoEnlace.COMPRADOR, ahora)
                .orElse(null);
        String tokenConductor = token(conductor);
        String tokenComprador = token(comprador);
        return new EnlacesVigentes(tokenConductor, tokenConductor == null ? null : conductor.fechaExpiracion(),
                tokenComprador, tokenComprador == null ? null : comprador.fechaExpiracion());
    }

    /** El token descifrado, solo si coincide con el hash guardado (si no, se trata como si no estuviera). */
    private String token(EnlaceEnvio enlace) {
        if (enlace == null || enlace.tokenCifrado() == null || cifrado == null) {
            return null;
        }
        return cifrado.descifrar(enlace.tokenCifrado())
                .filter(t -> generador.hash(t).equals(enlace.hashToken()))
                .orElse(null);
    }
}
