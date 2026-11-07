package pe.agroruta.infrastructure.adapter.out.correo;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.port.out.EnvioCorreoPort;

/** Manda los correos por SMTP (por ejemplo Gmail con una contraseña de aplicación). */
public class CorreoSmtpAdapter implements EnvioCorreoPort {

    private final JavaMailSender remitente;
    private final String desde;

    public CorreoSmtpAdapter(JavaMailSender remitente, String desde) {
        this.remitente = remitente;
        this.desde = desde;
    }

    @Override
    public void enviar(String correoDestino, String asunto, String cuerpo) {
        SimpleMailMessage mensaje = new SimpleMailMessage();
        mensaje.setFrom(desde);
        mensaje.setTo(correoDestino);
        mensaje.setSubject(asunto);
        mensaje.setText(cuerpo);
        try {
            remitente.send(mensaje);
        } catch (MailException e) {
            throw new ServicioExternoNoDisponibleException("El servidor de correo no aceptó el mensaje: " + e.getMostSpecificCause().getMessage(), e);
        }
    }
}
