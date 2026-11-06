package pe.agroruta.domain.model;

import java.time.Duration;
import java.time.Instant;

public record Posicion(Long idPosicion, Long idEnvio, Coordenada coordenada, Double precisionMetros,
                       Instant fechaPosicion, Instant fechaRecepcion) {

    /** Un celular sin señal puede enviar posiciones guardadas hasta un día después. */
    public static final Duration ANTIGUEDAD_MAXIMA = Duration.ofHours(24);
    /** Diferencia tolerada entre el reloj del celular y el del servidor. */
    public static final Duration DESFASE_RELOJ = Duration.ofMinutes(1);

    public Posicion {
        if (idEnvio == null || coordenada == null || fechaPosicion == null || fechaRecepcion == null) {
            throw new DominioException("La posición necesita envío, coordenadas y fechas");
        }
        if (precisionMetros != null && (precisionMetros < 0 || precisionMetros > 100_000)) {
            throw new DominioException("La precisión debe estar entre 0 y 100000 metros");
        }
    }

    public static Posicion nueva(Long idEnvio, double latitud, double longitud, Double precisionMetros,
                                 Instant fechaPosicion, Instant ahora) {
        if (fechaPosicion == null) {
            throw new DominioException("La fecha de la posición es obligatoria");
        }
        if (fechaPosicion.isAfter(ahora.plus(DESFASE_RELOJ))) {
            throw new DominioException("La fecha de la posición es futura");
        }
        if (fechaPosicion.isBefore(ahora.minus(ANTIGUEDAD_MAXIMA))) {
            throw new DominioException("La posición es demasiado antigua (más de 24 horas)");
        }
        return new Posicion(null, idEnvio, new Coordenada(latitud, longitud), precisionMetros, fechaPosicion, ahora);
    }

    public Posicion conId(Long nuevoIdPosicion) {
        return new Posicion(nuevoIdPosicion, idEnvio, coordenada, precisionMetros, fechaPosicion, fechaRecepcion);
    }
}
