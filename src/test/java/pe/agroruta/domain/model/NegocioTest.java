package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class NegocioTest {

    @Test
    void crea_un_negocio_valido_y_recorta_el_nombre() {
        Negocio negocio = Negocio.nuevo("chacra_demo", "  Chacra Demo  ", Plan.CHACRA);

        assertThat(negocio.nombre()).isEqualTo("Chacra Demo");
        assertThat(negocio.activo()).isTrue();
        assertThat(negocio.id()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "Chacra", "1chacra", "chacra-demo", "chacra demo", "x`; DROP DATABASE y; --",
            "a1234567890123456789012345678901"})
    void rechaza_identificadores_invalidos(String slug) {
        assertThatThrownBy(() -> Negocio.nuevo(slug, "Demo", Plan.EMPRESA))
                .isInstanceOf(DominioException.class);
    }

    @Test
    void rechaza_nombre_vacio_y_plan_nulo() {
        assertThatThrownBy(() -> Negocio.nuevo("demo", " ", Plan.EMPRESA)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> Negocio.nuevo("demo", "Demo", null)).isInstanceOf(DominioException.class);
    }
}
