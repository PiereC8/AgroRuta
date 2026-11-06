package pe.agroruta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class NombresBdTest {

    private final NombresBd nombres =
            new NombresBd("jdbc:mysql://localhost:3307/agroruta_plataforma_dev?useSSL=false", "agroruta_", "_dev");

    @Test
    void arma_el_nombre_de_la_base_del_negocio() {
        assertThat(nombres.deIdentificador("chacra_demo")).isEqualTo("agroruta_chacra_demo_dev");
    }

    @Test
    void cambia_solo_la_base_en_la_url() {
        assertThat(nombres.urlDeIdentificador("chacra_demo"))
                .isEqualTo("jdbc:mysql://localhost:3307/agroruta_chacra_demo_dev?useSSL=false");
    }

    @Test
    void agrega_la_creacion_automatica_de_la_base_central_una_sola_vez() {
        String url = NombresBd.creandoBaseSiFalta(nombres.urlPlataforma());

        assertThat(url).endsWith("&createDatabaseIfNotExist=true");
        assertThat(NombresBd.creandoBaseSiFalta(url)).isEqualTo(url);
        assertThat(NombresBd.creandoBaseSiFalta("jdbc:mysql://localhost:3307/x"))
                .isEqualTo("jdbc:mysql://localhost:3307/x?createDatabaseIfNotExist=true");
    }
}

