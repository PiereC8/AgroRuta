package pe.agroruta.domain.model;

import java.time.Instant;
import java.util.List;

/**
 * Camino que seguirá el vehículo desde donde está hasta el destino. Es la base para estimar el avance cuando se
 * pierde la señal: la posición estimada siempre cae sobre esta línea.
 *
 * @param porCarretera false si es una línea recta de respaldo (el servicio de rutas no respondió)
 */
public record Trayecto(List<Coordenada> puntos, double distanciaMetros, long duracionSegundos, boolean porCarretera,
                       Instant fechaCalculo) {

    private static final double RADIO_TIERRA_METROS = 6_371_000.0;

    public Trayecto {
        if (puntos == null || puntos.size() < 2) {
            throw new DominioException("El trayecto necesita al menos dos puntos");
        }
        if (distanciaMetros <= 0 || duracionSegundos <= 0 || fechaCalculo == null) {
            throw new DominioException("El trayecto necesita distancia, duración y fecha de cálculo");
        }
        puntos = List.copyOf(puntos);
    }

    /** Respaldo cuando no hay servicio de rutas: línea recta a la velocidad de referencia. */
    public static Trayecto lineaRecta(Coordenada origen, Coordenada destino, Instant ahora) {
        double metros = Math.max(1.0, origen.distanciaMetrosA(destino));
        long segundos = Math.max(1L, Math.round(metros / (ReglasSeguimiento.VELOCIDAD_REFERENCIA_KMH * 1000 / 3600)));
        return new Trayecto(List.of(origen, destino), metros, segundos, false, ahora);
    }

    /** Dónde cae una coordenada sobre el trayecto: cuánto se avanzó y a qué distancia de la línea está. */
    public record Medida(double metrosRecorridos, double metrosRestantes, double desvioMetros) {
    }

    /** Largo del trayecto sumando sus tramos (la misma medida que usa la pantalla). */
    public double longitudMetros() {
        double total = 0;
        for (int i = 1; i < puntos.size(); i++) {
            total += puntos.get(i - 1).distanciaMetrosA(puntos.get(i));
        }
        return total;
    }

    /** Velocidad media que implica la ruta (largo entre duración). */
    public double velocidadMediaKmh() {
        return longitudMetros() / duracionSegundos * 3.6;
    }

    /** Proyecta la coordenada sobre el tramo más cercano de la línea. */
    public Medida medirDesde(Coordenada c) {
        double total = longitudMetros();
        double mejorDesvio = Double.MAX_VALUE;
        double mejorRecorrido = 0;
        double acumulado = 0;
        for (int i = 1; i < puntos.size(); i++) {
            Coordenada a = puntos.get(i - 1);
            Coordenada b = puntos.get(i);
            double largo = a.distanciaMetrosA(b);
            // Plano local centrado en el punto: sirve para tramos cortos y no necesita trigonometría esférica.
            double coseno = Math.cos(Math.toRadians(c.latitud()));
            double ax = Math.toRadians(a.longitud() - c.longitud()) * coseno * RADIO_TIERRA_METROS;
            double ay = Math.toRadians(a.latitud() - c.latitud()) * RADIO_TIERRA_METROS;
            double bx = Math.toRadians(b.longitud() - c.longitud()) * coseno * RADIO_TIERRA_METROS;
            double by = Math.toRadians(b.latitud() - c.latitud()) * RADIO_TIERRA_METROS;
            double dx = bx - ax;
            double dy = by - ay;
            double cuadrado = dx * dx + dy * dy;
            double t = cuadrado == 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / cuadrado));
            double px = ax + t * dx;
            double py = ay + t * dy;
            double desvio = Math.sqrt(px * px + py * py);
            if (desvio < mejorDesvio) {
                mejorDesvio = desvio;
                mejorRecorrido = acumulado + t * largo;
            }
            acumulado += largo;
        }
        return new Medida(mejorRecorrido, Math.max(0, total - mejorRecorrido), mejorDesvio);
    }

    public List<Coordenada> puntos() {
        return puntos;
    }
}
