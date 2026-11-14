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
    /** Si el vehículo se aleja más que esto de la ruta guardada, se calcula una ruta nueva desde donde está. */
    public static final double DESVIO_MAXIMO_METROS = 1500;
    /** Cada cuánto se reintenta la ruta por carretera cuando solo hay una línea recta de respaldo. */
    public static final Duration REINTENTO_RUTA = Duration.ofMinutes(2);
    public static final Duration RETRASO_MINIMO = Duration.ofMinutes(15);
    public static final double RETRASO_PROPORCION = 0.20;

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
        double kmh = velocidadMediaKmh(posiciones, ultima, VELOCIDAD_REFERENCIA_KMH);
        double metrosPorMinuto = kmh * 1000 / 60;
        return OptionalLong.of((long) Math.ceil(metros / metrosPorMinuto));
    }

    /**
     * Minutos estimados hasta el destino medidos sobre la ruta (no en línea recta). Sin ruta, usa la línea recta.
     */
    public static OptionalLong etaMinutos(List<Posicion> posiciones, Coordenada destino, Trayecto trayecto) {
        if (trayecto == null || posiciones.isEmpty()) {
            return etaMinutos(posiciones, destino);
        }
        Posicion ultima = posiciones.get(posiciones.size() - 1);
        double restantes = trayecto.medirDesde(ultima.coordenada()).metrosRestantes();
        double kmh = velocidadKmh(posiciones, trayecto);
        return OptionalLong.of((long) Math.ceil(restantes / (kmh * 1000 / 60)));
    }

    /**
     * Velocidad con la que se proyecta el avance: la media reciente si el vehículo se mueve; si no, la que implica
     * la ruta, y si no hay ruta, la de referencia.
     */
    public static double velocidadKmh(List<Posicion> posiciones, Trayecto trayecto) {
        double respaldo = trayecto == null ? VELOCIDAD_REFERENCIA_KMH
                : Math.max(VELOCIDAD_MINIMA_KMH, trayecto.velocidadMediaKmh());
        if (posiciones.isEmpty()) {
            return respaldo;
        }
        return velocidadMediaKmh(posiciones, posiciones.get(posiciones.size() - 1), respaldo);
    }

    /** ¿Se salió el vehículo de la ruta guardada? */
    public static boolean desviado(Trayecto trayecto, Coordenada posicion) {
        return trayecto.medirDesde(posicion).desvioMetros() > DESVIO_MAXIMO_METROS;
    }

    /** Retraso si llegaría después de lo previsto más una tolerancia: lo mayor entre 15 min y el 20 % del viaje. */
    public static boolean hayRetraso(Instant llegadaPrevista, Instant llegadaEstimada, Duration duracionPrevista) {
        Duration proporcional = Duration.ofMillis((long) (duracionPrevista.toMillis() * RETRASO_PROPORCION));
        Duration tolerancia = proporcional.compareTo(RETRASO_MINIMO) > 0 ? proporcional : RETRASO_MINIMO;
        return llegadaEstimada.isAfter(llegadaPrevista.plus(tolerancia));
    }

    private static double velocidadMediaKmh(List<Posicion> posiciones, Posicion ultima, double respaldo) {
        Instant desde = ultima.fechaPosicion().minus(VENTANA_VELOCIDAD);
        List<Posicion> recientes = posiciones.stream().filter(p -> !p.fechaPosicion().isBefore(desde)).toList();
        if (recientes.size() < 2) {
            return respaldo;
        }
        double metros = 0;
        for (int i = 1; i < recientes.size(); i++) {
            metros += recientes.get(i - 1).coordenada().distanciaMetrosA(recientes.get(i).coordenada());
        }
        double horas = Duration.between(recientes.get(0).fechaPosicion(), ultima.fechaPosicion()).toMillis()
                / 3_600_000.0;
        if (horas <= 0) {
            return respaldo;
        }
        double kmh = metros / 1000 / horas;
        return kmh < VELOCIDAD_MINIMA_KMH ? respaldo : kmh;
    }
}
