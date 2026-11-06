package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.agroruta.domain.model.EnvioEnCurso;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.Panel;
import pe.agroruta.domain.port.in.ConsultarPanelUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

@RestController
@RequestMapping("/api/panel")
public class PanelController {

    private final ConsultarPanelUseCase consultarPanel;

    public PanelController(ConsultarPanelUseCase consultarPanel) {
        this.consultarPanel = consultarPanel;
    }

    @GetMapping("/resumen")
    public PanelRespuesta resumen(@AuthenticationPrincipal DatosToken usuario) {
        return PanelRespuesta.de(consultarPanel.consultar(usuario.rolUsuario()));
    }

    /** Los campos de ganancia faltan por completo (no van en null) cuando quien consulta no es el Dueño. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record PanelRespuesta(String mes, BigDecimal gananciaMes, BigDecimal gananciaMesAnterior,
                                 BigDecimal variacionPorcentual, BigDecimal mermaMesKg, BigDecimal mermaTotalKg,
                                 BigDecimal perdidaMermaMes, List<EnvioRespuesta> enviosEnCurso,
                                 CobrosRespuesta cobrosPendientes, List<MesRespuesta> gananciaUltimosMeses) {

        static PanelRespuesta de(Panel panel) {
            BigDecimal ganancia = panel.gananciaMes() == null ? null : panel.gananciaMes().monto();
            BigDecimal anterior = panel.gananciaMesAnterior() == null ? null : panel.gananciaMesAnterior().monto();
            return new PanelRespuesta(panel.mes().toString(), ganancia, anterior, variacion(ganancia, anterior),
                    panel.mermaMesKg(), panel.mermaTotalKg(), panel.perdidaMermaMes().monto(),
                    panel.enviosEnCurso().stream().map(EnvioRespuesta::de).toList(),
                    new CobrosRespuesta(panel.cobrosPendientes().monto().monto(),
                            panel.cobrosPendientes().cantidad()),
                    panel.gananciaUltimosMeses() == null ? null
                            : panel.gananciaUltimosMeses().stream()
                                    .map(m -> new MesRespuesta(m.mes().toString(), m.ganancia().monto())).toList());
        }

        /** Cambio porcentual con un decimal; no existe si el mes anterior fue cero (no se divide entre cero). */
        private static BigDecimal variacion(BigDecimal actual, BigDecimal anterior) {
            if (actual == null || anterior == null || anterior.signum() == 0) {
                return null;
            }
            return actual.subtract(anterior).multiply(BigDecimal.valueOf(100))
                    .divide(anterior, 1, RoundingMode.HALF_UP);
        }
    }

    public record EnvioRespuesta(Long idEnvio, EstadoEnvio estadoEnvio, String destino, String comprador,
                                 BigDecimal cantidadKg, Instant fechaProgramada) {
        static EnvioRespuesta de(EnvioEnCurso e) {
            return new EnvioRespuesta(e.idEnvio(), e.estadoEnvio(), e.destino(), e.comprador(), e.cantidadKg(),
                    e.fechaProgramada());
        }
    }

    public record CobrosRespuesta(BigDecimal monto, int cantidad) {
    }

    public record MesRespuesta(String mes, BigDecimal ganancia) {
    }
}
