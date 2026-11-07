package pe.agroruta.infrastructure.adapter.out.correo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.agroruta.domain.port.out.EnvioCorreoPort;

/** Para trabajar sin cuenta de correo: no manda nada, solo lo deja en el registro. El historial lo guarda la bandeja. */
public class CorreoSimuladoAdapter implements EnvioCorreoPort {

    private static final Logger REGISTRO = LoggerFactory.getLogger(CorreoSimuladoAdapter.class);

    @Override
    public void enviar(String correoDestino, String asunto, String cuerpo) {
        REGISTRO.info("[correo simulado] a {}: {}", correoDestino, asunto);
    }
}
