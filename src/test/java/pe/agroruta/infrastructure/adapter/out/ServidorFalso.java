package pe.agroruta.infrastructure.adapter.out;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;

import com.sun.net.httpserver.HttpServer;

/** Servidor HTTP local que graba lo que recibe y responde lo que se le indique. Sustituye a la pasarela y al proveedor. */
public final class ServidorFalso implements AutoCloseable {

    public record Peticion(String metodo, String ruta, java.util.Map<String, List<String>> cabeceras, String cuerpo) {
        public String cabecera(String nombre) {
            for (var entrada : cabeceras.entrySet()) {
                if (entrada.getKey().equalsIgnoreCase(nombre)) {
                    return entrada.getValue().get(0);
                }
            }
            return null;
        }
    }

    private record Respuesta(int estado, String cuerpo) {
    }

    public final List<Peticion> peticiones = new ArrayList<>();
    private final Deque<Respuesta> respuestas = new ArrayDeque<>();
    private final HttpServer servidor;

    public ServidorFalso() {
        try {
            servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
        servidor.createContext("/", intercambio -> {
            String cuerpo = new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8);
            synchronized (peticiones) {
                peticiones.add(new Peticion(intercambio.getRequestMethod(), intercambio.getRequestURI().getPath(),
                        new java.util.HashMap<>(intercambio.getRequestHeaders()), cuerpo));
            }
            Respuesta respuesta;
            synchronized (respuestas) {
                respuesta = respuestas.size() > 1 ? respuestas.poll() : respuestas.peek();
            }
            byte[] bytes = (respuesta == null ? "{}" : respuesta.cuerpo()).getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(respuesta == null ? 200 : respuesta.estado(), bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
    }

    /** Agrega una respuesta. Se entregan en orden; la última se repite. */
    public ServidorFalso responde(int estado, String cuerpoJson) {
        synchronized (respuestas) {
            respuestas.add(new Respuesta(estado, cuerpoJson));
        }
        return this;
    }

    public String url() {
        return "http://127.0.0.1:" + servidor.getAddress().getPort();
    }

    public Peticion ultima() {
        synchronized (peticiones) {
            return peticiones.get(peticiones.size() - 1);
        }
    }

    @Override
    public void close() {
        servidor.stop(0);
    }
}
