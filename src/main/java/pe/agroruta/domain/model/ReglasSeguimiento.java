package pe.agroruta.domain.model;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.OptionalLong;

public final class ReglasSeguimiento {

    public static final double RADIO_LLEGADA_METROS = 300;
    public static final double RADIO_DETENIDO_METROS = 50;
    public static final Duration VENTANA_DETENIDO = Duration.ofMinutes(15);
    public static final Duration TOLERANCIA = Duration.ofMinutes(2);
    public static final Duration VENTANA_VELOCIDAD = Duration.ofMinutes(10);
    public static final double VELOCIDAD_REFERENCIA_KMH = 40;
    public static final double VELOCIDAD_MINIMA_KMH = 5;

    private ReglasSeguimiento() {
    }

    public static boolean llego(Coordenada actual, Coordenada destino) {
        return actual.distanciaMetrosA(destino) <= RADIO_LLEGADA_METROS;
    }

    /**
     * El vehículo está detenido si, durante toda la ventana de detención, todas las posiciones quedaron
     * dentro de un radio pequeño de la última. Se necesitan posiciones que cubran la ventana completa.
     *
     * @param posiciones ordenadas de la más antigua a la más reciente
     */
    public static boolean detenido(List<Posicion> posiciones, Instant ahora) {
        Instant desde = ahora.minus(VENTANA_DETENIDO).minus(TOLERANCIA);
        List<Posicion> enVentana = posiciones.stream().filter(p -> !p.fechaPosicion().isBefore(desde)).toList();
        if (enVentana.size() < 2) {
            return false;
        }
        Posicion primera = enVentana.get(0);
        Posicion ultima = enVentana.get(enVentana.size() - 1);
        if (ultima.fechaPosicion().isBefore(ahora.minus(TOLERANCIA))) {
            return false;
        }
        Duration cobertura = Duration.between(primera.fechaPosicion(), ultima.fechaPosicion());
        if (cobertura.compareTo(VENTANA_DETENIDO.minus(TOLERANCIA)) < 0) {
            return false;
        }
        return enVentana.stream().allMatch(
                p -> p.coordenada().distanciaMetrosA(ultima.coordenada()) <= RADIO_DETENIDO_METROS);
    }

    /**
     * Minutos estimados hasta el destino. Usa la velocidad media reciente si el vehículo se mueve; si no, la de
     * referencia.
     *
     * @param posiciones ordenadas de la más antigua a la más reciente
     */
    public static OptionalLong etaMinutos(List<Posicion> posiciones, Coordenada destino) {
        if (posiciones.isEmpty()) {
            return OptionalLong.empty();
        }
        Posicion ultima = posiciones.get(posiciones.size() - 1);
        double metros = ultima.coordenada().distanciaMetrosA(destino);
        double kmh = velocidadMediaKmh(posiciones, ultima);
        double metrosPorMinuto = kmh * 1000 / 60;
        return OptionalLong.of((long) Math.ceil(metros / metrosPorMinuto));
    }

    private static double velocidadMediaKmh(List<Posicion> posiciones, Posicion ultima) {
        Instant desde = ultima.fechaPosicion().minus(VENTANA_VELOCIDAD);
        List<Posicion> recientes = posiciones.stream().filter(p -> !p.fechaPosicion().isBefore(desde)).toList();
        if (recientes.size() < 2) {
            return VELOCIDAD_REFERENCIA_KMH;
        }
        double metros = 0;
        for (int i = 1; i < recientes.size(); i++) {
            metros += recientes.get(i - 1).coordenada().distanciaMetrosA(recientes.get(i).coordenada());
        }
        double horas = Duration.between(recientes.get(0).fechaPosicion(), ultima.fechaPosicion()).toMillis()
                / 3_600_000.0;
        if (horas <= 0) {
            return VELOCIDAD_REFERENCIA_KMH;
        }
        double kmh = metros / 1000 / horas;
        return kmh < VELOCIDAD_MINIMA_KMH ? VELOCIDAD_REFERENCIA_KMH : kmh;
    }
}
