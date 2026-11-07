package pe.agroruta.domain.port.out;

/** Manda un correo de texto. Lanza una excepción si no se pudo enviar. */
public interface EnvioCorreoPort {

    void enviar(String correoDestino, String asunto, String cuerpo);
}
