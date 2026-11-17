package pe.agroruta.domain.port.out;

import java.util.Optional;

/** Cifra un texto corto (por ejemplo el token de un enlace) para guardarlo y poder leerlo después. */
public interface CifradoPort {

    String cifrar(String texto);

    /** Vacío si no se puede descifrar (por ejemplo, si cambió la clave): se trata como si no estuviera guardado. */
    Optional<String> descifrar(String cifrado);
}
