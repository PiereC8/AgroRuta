package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class EstadoCobroTest {

    @Test
    void compara_lo_cobrado_con_lo_que_hay_que_cobrar() {
        assertThat(EstadoCobro.segun(null)).isEqualTo(EstadoCobro.SIN_ENTREGAR);
        assertThat(EstadoCobro.segun(new ResumenCobro(Dinero.de("500.00"), Dinero.CERO)))
                .isEqualTo(EstadoCobro.POR_COBRAR);
        assertThat(EstadoCobro.segun(new ResumenCobro(Dinero.de("500.00"), Dinero.de("200.00"))))
                .isEqualTo(EstadoCobro.PARCIAL);
        assertThat(EstadoCobro.segun(new ResumenCobro(Dinero.de("500.00"), Dinero.de("500.00"))))
                .isEqualTo(EstadoCobro.COBRADO);
        // Si después de cobrar se corrigió la entrega a la baja, el pedido sigue cobrado.
        assertThat(EstadoCobro.segun(new ResumenCobro(Dinero.de("450.00"), Dinero.de("500.00"))))
                .isEqualTo(EstadoCobro.COBRADO);
    }
}
