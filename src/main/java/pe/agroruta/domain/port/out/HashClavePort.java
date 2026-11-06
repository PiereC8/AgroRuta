package pe.agroruta.domain.port.out;

public interface HashClavePort {

    String cifrar(String clave);

    boolean coincide(String clave, String hashClave);
}
