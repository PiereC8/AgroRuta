package pe.agroruta.domain.port.out;

public interface GeneradorTokenPort {

    /** Un token aleatorio imposible de adivinar. */
    String nuevoToken();

    String hash(String token);
}
