package pe.agroruta.infrastructure.adapter.out.ruta;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.Trayecto;
import pe.agroruta.domain.port.out.RutaPort;
import pe.agroruta.infrastructure.adapter.out.http.ClienteHttp;

/** Camino por carretera con la API de rutas de OSRM. Solo se envían dos coordenadas, ningún dato del negocio. */
public class RutaOsrmAdapter implements RutaPort {

    private final ObjectMapper json = new ObjectMapper();
    private final ClienteHttp http;
    private final String urlBase;

    public RutaOsrmAdapter(ClienteHttp http, String urlBase) {
        this.http = http;
        this.urlBase = urlBase.endsWith("/") ? urlBase.substring(0, urlBase.length() - 1) : urlBase;
    }

    @Override
    public Trayecto calcular(Coordenada origen, Coordenada destino, Instant ahora) {
        String url = String.format(Locale.ROOT, "%s/route/v1/driving/%.6f,%.6f;%.6f,%.6f"
                        + "?overview=simplified&geometries=geojson", urlBase, origen.longitud(), origen.latitud(),
                destino.longitud(), destino.latitud());
        ClienteHttp.Respuesta respuesta = http.enviar("GET", url, null);
        if (respuesta.estado() != 200) {
            throw new ServicioExternoNoDisponibleException("El servicio de rutas respondió " + respuesta.estado());
        }
        try {
            JsonNode ruta = json.readTree(respuesta.cuerpo()).path("routes").path(0);
            JsonNode coordenadas = ruta.path("geometry").path("coordinates");
            if (!coordenadas.isArray() || coordenadas.size() < 2) {
                throw new ServicioExternoNoDisponibleException("El servicio de rutas no encontró un camino");
            }
            List<Coordenada> puntos = new ArrayList<>(coordenadas.size());
            for (JsonNode c : coordenadas) {
                puntos.add(new Coordenada(c.get(1).asDouble(), c.get(0).asDouble()));
            }
            return new Trayecto(puntos, ruta.path("distance").asDouble(), Math.round(ruta.path("duration").asDouble()),
                    true, ahora);
        } catch (JsonProcessingException e) {
            throw new ServicioExternoNoDisponibleException("La respuesta del servicio de rutas no se pudo leer", e);
        }
    }
}
