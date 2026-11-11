package pe.agroruta.infrastructure.adapter.in.web;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Limita por IP las peticiones a /api/auth/** (entrar, pedir y usar el enlace para cambiar la contraseña). Complementa
 * el bloqueo por cuenta: frena a quien prueba una clave contra muchos usuarios. Cuenta en memoria, por ventanas de un
 * minuto; al reiniciar el servidor el conteo vuelve a cero.
 *
 * <p>La IP sale del primer valor de X-Forwarded-For (lo pone el proxy de Vercel/Render); sin él, de la conexión.
 * Quien llame directo al servidor puede falsear ese encabezado: para eso sigue el bloqueo de 15 minutos por cuenta.
 */
public class LimiteIntentosFiltro extends OncePerRequestFilter {

    private static final long VENTANA_MS = 60_000;
    private static final int MAXIMO_IPS_EN_MEMORIA = 10_000;
    static final String MENSAJE = "Demasiados intentos desde tu conexión. Espera un minuto e inténtalo de nuevo.";

    private record Ventana(long inicioMs, int cuenta) {
    }

    private final int maximoPorMinuto;
    private final LongSupplier milisegundos;
    private final Map<String, Ventana> ventanas = new ConcurrentHashMap<>();

    public LimiteIntentosFiltro(int maximoPorMinuto, Clock reloj) {
        this(maximoPorMinuto, reloj::millis);
    }

    LimiteIntentosFiltro(int maximoPorMinuto, LongSupplier milisegundos) {
        this.maximoPorMinuto = maximoPorMinuto;
        this.milisegundos = milisegundos;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest peticion) {
        return !"POST".equals(peticion.getMethod()) || !peticion.getRequestURI().startsWith("/api/auth/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        long ahora = milisegundos.getAsLong();
        if (ventanas.size() > MAXIMO_IPS_EN_MEMORIA) {
            ventanas.values().removeIf(v -> ahora - v.inicioMs() >= VENTANA_MS);
        }
        Ventana ventana = ventanas.compute(ipDe(peticion), (ip, v) -> v == null || ahora - v.inicioMs() >= VENTANA_MS
                ? new Ventana(ahora, 1) : new Ventana(v.inicioMs(), v.cuenta() + 1));
        if (ventana.cuenta() > maximoPorMinuto) {
            long segundos = Math.max(1, (ventana.inicioMs() + VENTANA_MS - ahora + 999) / 1000);
            respuesta.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            respuesta.setHeader("Retry-After", String.valueOf(segundos));
            respuesta.setContentType(MediaType.APPLICATION_JSON_VALUE);
            respuesta.setCharacterEncoding(StandardCharsets.UTF_8.name());
            respuesta.getWriter().write("{\"error\":\"" + MENSAJE + "\"}");
            return;
        }
        cadena.doFilter(peticion, respuesta);
    }

    static String ipDe(HttpServletRequest peticion) {
        String reenviada = peticion.getHeader("X-Forwarded-For");
        if (reenviada != null && !reenviada.isBlank()) {
            String primera = reenviada.split(",")[0].strip();
            if (!primera.isEmpty() && primera.length() <= 45) {
                return primera;
            }
        }
        return peticion.getRemoteAddr();
    }
}
