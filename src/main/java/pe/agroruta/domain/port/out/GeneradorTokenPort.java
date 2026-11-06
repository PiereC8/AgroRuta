package pe.agroruta.domain.port.out;

public interface GeneradorTokenPort {

    String nuevoToken();

    String hash(String token);
}
