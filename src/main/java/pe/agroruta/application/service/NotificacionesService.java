package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.agroruta.domain.model.Aviso;
import pe.agroruta.domain.model.Correo;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Notificacion;
import pe.agroruta.domain.model.TipoAviso;
import pe.agroruta.domain.port.in.ConsultarNotificacionesUseCase;
import pe.agroruta.domain.port.in.DespacharNotificacionesUseCase;
import pe.agroruta.domain.port.out.EnvioCorreoPort;
import pe.agroruta.domain.port.out.NotificacionRepositoryPort;

public class NotificacionesService implements DespacharNotificacionesUseCase, ConsultarNotificacionesUseCase {

    private static final Logger REGISTRO = LoggerFactory.getLogger(NotificacionesService.class);
    private static final int LOTE = 50;

    private final NotificacionRepositoryPort bandeja;
    private final EnvioCorreoPort correo;
    private final Clock reloj;

    public NotificacionesService(NotificacionRepositoryPort bandeja, EnvioCorreoPort correo, Clock reloj) {
        this.bandeja = bandeja;
        this.correo = correo;
        this.reloj = reloj;
    }

    @Override
    public int despachar() {
        int enviados = 0;
        for (Notificacion pendiente : bandeja.pendientes(reloj.instant(), LOTE)) {
            try {
                correo.enviar(pendiente.correoDestino(), pendiente.asunto(), pendiente.cuerpo());
                bandeja.guardar(pendiente.comoEnviada(reloj.instant()));
                enviados++;
            } catch (RuntimeException e) {
                REGISTRO.warn("No se pudo enviar el correo {}: {}", pendiente.idNotificacion(), e.getMessage());
                bandeja.guardar(pendiente.conFallo(e.getMessage(), reloj.instant()));
            }
        }
        return enviados;
    }

    @Override
    public void probar(String correoDestino) {
        String destino = Correo.normalizar(correoDestino);
        if (destino == null) {
            throw new DominioException("Escribe el correo al que quieres mandar la prueba");
        }
        String asunto = "Prueba de correo de AgroRuta";
        try {
            correo.enviar(destino, asunto, "Si lees este mensaje, los avisos de AgroRuta llegan a este correo.");
        } catch (RuntimeException e) {
            REGISTRO.warn("Falló el correo de prueba: {}", e.getMessage());
            throw new DominioException("No se pudo enviar el correo de prueba. Revisa la configuración del correo: "
                    + e.getMessage());
        }
        Instant ahora = reloj.instant();
        Notificacion enviada = Notificacion.nueva(new Aviso("prueba:" + ahora.toEpochMilli(), TipoAviso.PRUEBA,
                asunto, "Correo de prueba"), destino, ahora).comoEnviada(ahora);
        bandeja.encolar(enviada);
    }

    @Override
    public List<Notificacion> recientes(int limite) {
        return bandeja.recientes(limite);
    }
}
