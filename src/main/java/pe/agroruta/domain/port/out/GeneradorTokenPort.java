package pe.agroruta.domain.port.out;

public interface GeneradorTokenPort {

    String nuevoToken();

    String hash(String token);

    /** Código de 6 dígitos al azar (con ceros a la izquierda), para la verificación en dos pasos. */
    String nuevoCodigo();
}
