package pe.agroruta.infrastructure.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

class TenantRoutingDataSourceTest {

    private final List<String> creados = new ArrayList<>();
    private final TenantRoutingDataSource ruteo = new TenantRoutingDataSource(identificadorNegocio -> {
        creados.add(identificadorNegocio);
        return mock(DataSource.class);
    });

    @AfterEach
    void limpiar() {
        TenantContext.limpiar();
    }

    @Test
    void sin_negocio_activo_rechaza_la_conexion() {
        assertThatThrownBy(ruteo::getConnection).isInstanceOf(IllegalStateException.class);
        assertThat(creados).isEmpty();
    }

    @Test
    void crea_un_pool_por_negocio_y_lo_reutiliza() throws SQLException {
        TenantContext.fijar("chacra_demo");
        ruteo.getConnection();
        ruteo.getConnection();
        TenantContext.fijar("empresa_demo");
        ruteo.getConnection();

        assertThat(creados).containsExactly("chacra_demo", "empresa_demo");
    }

    @Test
    void el_contexto_se_limpia() {
        TenantContext.fijar("chacra_demo");
        TenantContext.limpiar();

        assertThat(TenantContext.actual()).isEmpty();
    }
}

