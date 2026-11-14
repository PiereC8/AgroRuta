package pe.agroruta.infrastructure.adapter.out.ruta;

import java.time.Instant;

import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.Trayecto;
import pe.agroruta.domain.port.out.RutaPort;

/** Sin servicio de rutas: una línea recta entre los dos puntos. */
public class RutaLinealAdapter implements RutaPort {

    @Override
    public Trayecto calcular(Coordenada origen, Coordenada destino, Instant ahora) {
        return Trayecto.lineaRecta(origen, destino, ahora);
    }
}
