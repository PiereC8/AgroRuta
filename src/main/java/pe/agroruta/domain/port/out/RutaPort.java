package pe.agroruta.domain.port.out;

import java.time.Instant;

import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.Trayecto;

/** Calcula el camino por carretera entre dos puntos. Lanza ServicioExternoNoDisponibleException si no puede. */
public interface RutaPort {

    Trayecto calcular(Coordenada origen, Coordenada destino, Instant ahora);
}
