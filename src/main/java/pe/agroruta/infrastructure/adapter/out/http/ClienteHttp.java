package pe.agroruta.infrastructure.adapter.out.http;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;

/** Cliente HTTP con tiempos límite. Cualquier falla de red se convierte en "servicio no disponible". */
public class ClienteHttp {

    public record Respuesta(int estado, String cuerpo) {
    }

    private final HttpClient cliente;
    private final Duration tiempoLimite;

    public ClienteHttp(Duration tiempoConexion, Duration tiempoLimite) {
        this.cliente = HttpClient.newBuilder().connectTimeout(tiempoConexion).build();
        this.tiempoLimite = tiempoLimite;
    }

    public static ClienteHttp porDefecto() {
        return new ClienteHttp(Duration.ofSeconds(5), Duration.ofSeconds(20));
    }

    public Respuesta enviar(String metodo, String url, String cuerpoJson, String... cabeceras) {
        HttpRequest.Builder peticion = HttpRequest.newBuilder(URI.create(url)).timeout(tiempoLimite);
        for (int i = 0; i + 1 < cabeceras.length; i += 2) {
            peticion.header(cabeceras[i], cabeceras[i + 1]);
        }
        if (cuerpoJson == null) {
            peticion.method(metodo, HttpRequest.BodyPublishers.noBody());
        } else {
            peticion.header("Content-Type", "application/json");
            peticion.method(metodo, HttpRequest.BodyPublishers.ofString(cuerpoJson));
        }
        try {
            HttpResponse<String> respuesta = cliente.send(peticion.build(), HttpResponse.BodyHandlers.ofString());
            return new Respuesta(respuesta.statusCode(), respuesta.body());
        } catch (IOException e) {
            throw new ServicioExternoNoDisponibleException("No se pudo conectar con " + URI.create(url).getHost(), e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new ServicioExternoNoDisponibleException("La conexión fue interrumpida", e);
        }
    }
}
