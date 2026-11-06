package pe.agroruta.infrastructure.adapter.in.websocket;

import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.Posicion;
import pe.agroruta.domain.model.TipoAlerta;
import pe.agroruta.domain.port.out.PublicadorSeguimientoPort;

@Component
public class PublicadorSeguimientoStomp implements PublicadorSeguimientoPort {

    /** Las fechas viajan como texto ISO-8601 para que cualquier cliente las lea igual. */
    public record MensajePosicion(String tipo, double latitud, double longitud, Double precisionMetros,
                                  String fecha, Long etaMinutos) {
    }

    public record MensajeAlerta(String tipo, TipoAlerta tipoAlerta, String descripcion, String fecha) {
    }

    private final SimpMessagingTemplate plantilla;

    public PublicadorSeguimientoStomp(SimpMessagingTemplate plantilla) {
        this.plantilla = plantilla;
    }

    @Override
    public void publicarPosicion(String identificadorNegocio, Long idEnvio, Posicion posicion, Long etaMinutos) {
        plantilla.convertAndSend(Canales.deEnvio(identificadorNegocio, idEnvio),
                new MensajePosicion("POSICION", posicion.coordenada().latitud(), posicion.coordenada().longitud(),
                        posicion.precisionMetros(), posicion.fechaPosicion().toString(), etaMinutos));
    }

    @Override
    public void publicarAlerta(String identificadorNegocio, Long idEnvio, AlertaEnvio alerta) {
        plantilla.convertAndSend(Canales.deEnvio(identificadorNegocio, idEnvio),
                new MensajeAlerta("ALERTA", alerta.tipoAlerta(), alerta.descripcionAlerta(),
                        alerta.fechaAlerta().toString()));
    }
}
