package pe.agroruta.infrastructure.adapter.out.comprobante;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.DatosFiscales;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.port.out.DatosFiscalesPort;
import pe.agroruta.infrastructure.config.TenantContext;

class EmisorPorNegocioTest {

    private static final DatosEmisor RESPALDO = new DatosEmisor("20000000001", "AGRORUTA DEMO", "AGRORUTA",
            "AV. BOLOGNESI 100", "230101", "TACNA", "TACNA", "TACNA");

    private final Map<String, DatosFiscales> guardados = new HashMap<>();
    private final DatosFiscalesPort puerto = new DatosFiscalesPort() {
        @Override
        public Optional<DatosFiscales> buscar(String identificadorNegocio) {
            return Optional.ofNullable(guardados.get(identificadorNegocio));
        }

        @Override
        public boolean guardar(String identificadorNegocio, DatosFiscales datos) {
            guardados.put(identificadorNegocio, datos);
            return true;
        }
    };

    @Test
    void cada_negocio_emite_con_sus_datos_y_sin_ellos_se_usa_el_respaldo() {
        guardados.put("sol_de_tacna", new DatosFiscales("20612345678", "SOL DE TACNA S.A.C.", "Av. Leguía 200"));
        EmisorPorNegocio emisor = new EmisorPorNegocio(puerto, RESPALDO);

        DatosEmisor sol = TenantContext.ejecutarEn("sol_de_tacna", emisor::get);
        DatosEmisor otro = TenantContext.ejecutarEn("chacra_sama", emisor::get);

        assertThat(sol.ruc()).isEqualTo("20612345678");
        assertThat(sol.razonSocial()).isEqualTo("SOL DE TACNA S.A.C.");
        assertThat(sol.domicilioFiscal()).isEqualTo("Av. Leguía 200");
        assertThat(otro).isEqualTo(RESPALDO);
        assertThat(emisor.get()).isEqualTo(RESPALDO);
    }

    @Test
    void el_ruc_debe_ser_valido() {
        assertThatThrownBy(() -> new DatosFiscales("123", "X", "Y")).isInstanceOf(DominioException.class)
                .hasMessageContaining("11 dígitos");
        assertThatThrownBy(() -> new DatosFiscales("20612345678", " ", "Y")).isInstanceOf(DominioException.class);
    }
}
