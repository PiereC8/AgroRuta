package pe.agroruta.domain.model;

/** Un archivo listo para descargar. */
public record Archivo(String nombre, String tipoContenido, byte[] contenido) {
}
