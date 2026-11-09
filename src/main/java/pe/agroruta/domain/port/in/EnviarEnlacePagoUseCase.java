package pe.agroruta.domain.port.in;

import java.time.Instant;

/** Manda al correo del comprador un enlace nuevo para pagar un pedido entregado. */
public interface EnviarEnlacePagoUseCase {

    Resultado enviar(Comando comando);

    /** urlBase: la dirección del sitio, por ejemplo https://agroruta.duckdns.org. */
    record Comando(String identificadorNegocio, Long idEnvio, String correoComprador, String urlBase) {
    }

    record Resultado(String correo, String urlPago, Instant fechaExpiracion) {
    }
}
