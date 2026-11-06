package pe.agroruta.infrastructure.config;

import java.io.Closeable;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import javax.sql.DataSource;

import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

public class TenantRoutingDataSource extends AbstractRoutingDataSource implements DisposableBean {

    private final Function<String, DataSource> fabrica;
    private final Map<String, DataSource> pools = new ConcurrentHashMap<>();

    public TenantRoutingDataSource(Function<String, DataSource> fabrica) {
        this.fabrica = fabrica;
        setTargetDataSources(new HashMap<>());
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.actual().orElse(null);
    }

    @Override
    protected DataSource determineTargetDataSource() {
        String identificadorNegocio = TenantContext.actual()
                .orElseThrow(() -> new IllegalStateException("No hay un negocio activo en esta petición"));
        return pools.computeIfAbsent(identificadorNegocio, fabrica);
    }

    @Override
    public void destroy() throws IOException {
        for (DataSource pool : pools.values()) {
            if (pool instanceof Closeable cerrable) {
                cerrable.close();
            }
        }
        pools.clear();
    }
}
