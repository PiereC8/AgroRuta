package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.HechoDelNegocio.AlertaDeEnvio;
import pe.agroruta.domain.model.HechoDelNegocio.CobroVencido;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteAtascado;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteRechazado;
import pe.agroruta.domain.model.HechoDelNegocio.EntregaRegistrada;
import pe.agroruta.domain.model.HechoDelNegocio.PagoRecibido;

class PoliticaAvisosTest {

    private final PoliticaAvisos politica = new PoliticaAvisos(new BigDecimal("10"));

    private static EntregaRegistrada entrega(String ganancia, String merma, String rechazo) {
        return new EntregaRegistrada(7L, "Ana Torres", "Palta Hass", "Mercado Lima", new BigDecimal("100.000"),
                new BigDecimal(merma), new BigDecimal(rechazo), Dinero.de(ganancia), Dinero.de("120.00"));
    }

    @Test
    void una_entrega_normal_no_avisa_nada() {
        assertThat(politica.avisosDe(entrega("800.00", "3.000", "2.000"))).isEmpty();
    }

    @Test
    void una_entrega_con_ganancia_negativa_avisa_al_dueno_con_el_monto() {
        List<Aviso> avisos = politica.avisosDe(entrega("-50.00", "3.000", "2.000"));

        assertThat(avisos).hasSize(1);
        Aviso aviso = avisos.get(0);
        assertThat(aviso.tipo()).isEqualTo(TipoAviso.ENTREGA_CON_PERDIDA);
        assertThat(aviso.clave()).isEqualTo("entrega-con-perdida:7");
        assertThat(aviso.asunto()).contains("pérdida");
        assertThat(aviso.cuerpo()).contains("S/ 50.00").contains("Ana Torres").contains("Mercado Lima");
        assertThat(aviso.tipo().destinatarios()).containsExactly(Rol.DUENO);
    }

    @Test
    void la_merma_y_el_rechazo_sobre_el_umbral_avisan_aunque_se_haya_ganado() {
        List<Aviso> avisos = politica.avisosDe(entrega("300.00", "8.000", "4.000"));

        assertThat(avisos).extracting(Aviso::tipo).containsExactly(TipoAviso.MERMA_ALTA);
        assertThat(avisos.get(0).cuerpo()).contains("12");
    }

    @Test
    void justo_en_el_umbral_no_avisa() {
        assertThat(politica.avisosDe(entrega("300.00", "6.000", "4.000"))).isEmpty();
    }

    @Test
    void una_entrega_con_perdida_y_merma_alta_genera_los_dos_avisos() {
        assertThat(politica.avisosDe(entrega("-10.00", "20.000", "0.000")))
                .extracting(Aviso::tipo).containsExactlyInAnyOrder(TipoAviso.ENTREGA_CON_PERDIDA, TipoAviso.MERMA_ALTA);
    }

    @Test
    void un_pago_recibido_avisa_al_dueno() {
        Aviso aviso = politica.avisosDe(new PagoRecibido(4L, "Ana Torres", Dinero.de("1500.00"), 7L)).get(0);

        assertThat(aviso.tipo()).isEqualTo(TipoAviso.PAGO_RECIBIDO);
        assertThat(aviso.clave()).isEqualTo("pago-recibido:4");
        assertThat(aviso.cuerpo()).contains("S/ 1,500.00").contains("Ana Torres");
    }

    @Test
    void un_comprobante_rechazado_o_atascado_avisa_al_dueno() {
        Aviso rechazado = politica.avisosDe(new ComprobanteRechazado(3L, "B001-00000012", "RUC inválido")).get(0);
        Aviso atascado = politica.avisosDe(new ComprobanteAtascado(5L, "B001-00000013", 30)).get(0);

        assertThat(rechazado.clave()).isEqualTo("comprobante-rechazado:3");
        assertThat(rechazado.cuerpo()).contains("B001-00000012").contains("RUC inválido");
        assertThat(atascado.clave()).isEqualTo("comprobante-atascado:5");
        assertThat(atascado.cuerpo()).contains("30 horas");
    }

    @Test
    void un_cobro_vencido_avisa_al_dueno_con_los_dias() {
        Aviso aviso = politica.avisosDe(new CobroVencido(7L, "Ana Torres", Dinero.de("2400.00"), 9)).get(0);

        assertThat(aviso.tipo()).isEqualTo(TipoAviso.COBRO_VENCIDO);
        assertThat(aviso.clave()).isEqualTo("cobro-vencido:7");
        assertThat(aviso.cuerpo()).contains("9 días").contains("S/ 2,400.00");
    }

    @Test
    void las_alertas_del_envio_van_al_operador_y_al_dueno_salvo_la_llegada_que_va_al_operador() {
        Aviso detenido = politica.avisosDe(new AlertaDeEnvio(11L, 6L, TipoAlerta.DETENIDO, "Mercado Lima",
                "Detenido 15 minutos")).get(0);
        Aviso gps = politica.avisosDe(new AlertaDeEnvio(12L, 6L, TipoAlerta.GPS_DESACTIVADO, "Mercado Lima",
                "GPS apagado")).get(0);
        Aviso llegada = politica.avisosDe(new AlertaDeEnvio(13L, 6L, TipoAlerta.LLEGADA, "Mercado Lima",
                "Llegó")).get(0);

        assertThat(detenido.tipo().destinatarios()).isEqualTo(Set.of(Rol.OPERADOR, Rol.DUENO));
        assertThat(gps.tipo().destinatarios()).isEqualTo(Set.of(Rol.OPERADOR, Rol.DUENO));
        assertThat(llegada.tipo().destinatarios()).containsExactly(Rol.OPERADOR);
        assertThat(detenido.clave()).isEqualTo("alerta-envio:11");
        assertThat(detenido.cuerpo()).contains("envío 6").contains("Mercado Lima");
    }

    @Test
    void el_retraso_del_envio_avisa_al_operador_y_al_dueno_con_su_propio_tipo_y_titulo() {
        Aviso retraso = politica.avisosDe(new AlertaDeEnvio(14L, 6L, TipoAlerta.RETRASO, "Mercado Lima",
                "El envío lleva un retraso de unos 55 minutos respecto de lo previsto")).get(0);

        assertThat(retraso.tipo()).isEqualTo(TipoAviso.ENVIO_RETRASADO);
        assertThat(retraso.tipo().destinatarios()).isEqualTo(Set.of(Rol.OPERADOR, Rol.DUENO));
        assertThat(retraso.clave()).isEqualTo("alerta-envio:14");
        assertThat(retraso.asunto()).contains("Envío con retraso").contains("envío 6");
        assertThat(retraso.cuerpo()).contains("Mercado Lima").contains("55 minutos");
    }
}
