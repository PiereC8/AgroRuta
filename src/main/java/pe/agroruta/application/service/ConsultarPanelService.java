package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import pe.agroruta.domain.model.GananciaDelMes;
import pe.agroruta.domain.model.Panel;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.ConsultarPanelUseCase;
import pe.agroruta.domain.port.out.PanelRepositoryPort;

public class ConsultarPanelService implements ConsultarPanelUseCase {

    static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final int MESES_DE_SERIE = 6;
    private static final int LIMITE_DE_ENVIOS = 10;

    private final PanelRepositoryPort panel;
    private final Clock reloj;

    public ConsultarPanelService(PanelRepositoryPort panel, Clock reloj) {
        this.panel = panel;
        this.reloj = reloj;
    }

    @Override
    public Panel consultar(Rol rol) {
        YearMonth mes = YearMonth.now(reloj.withZone(LIMA));
        Instant inicio = inicioDe(mes);
        Instant fin = inicioDe(mes.plusMonths(1));
        boolean verGanancia = rol == Rol.DUENO;

        List<GananciaDelMes> serie = null;
        if (verGanancia) {
            serie = new ArrayList<>();
            for (int atras = MESES_DE_SERIE - 1; atras >= 0; atras--) {
                YearMonth m = mes.minusMonths(atras);
                serie.add(new GananciaDelMes(m, panel.gananciaEntre(inicioDe(m), inicioDe(m.plusMonths(1)))));
            }
        }
        // El mes anterior se compara hasta el mismo día y hora: el día 3 contra el 1 al 3 del mes pasado,
        // no contra el mes completo (que haría parecer una caída del 90 %).
        Instant mismoMomentoMesPasado = LocalDateTime.now(reloj.withZone(LIMA)).minusMonths(1).atZone(LIMA).toInstant();
        return new Panel(mes,
                verGanancia ? panel.gananciaEntre(inicio, fin) : null,
                verGanancia ? panel.gananciaEntre(inicioDe(mes.minusMonths(1)), mismoMomentoMesPasado) : null,
                panel.mermaKgEntre(inicio, fin),
                panel.mermaKgEntre(Instant.EPOCH, fin),
                panel.perdidaEntre(inicio, fin),
                panel.enviosEnCurso(LIMITE_DE_ENVIOS),
                panel.cobrosPendientes(),
                serie);
    }

    private static Instant inicioDe(YearMonth mes) {
        return mes.atDay(1).atStartOfDay(LIMA).toInstant();
    }
}
