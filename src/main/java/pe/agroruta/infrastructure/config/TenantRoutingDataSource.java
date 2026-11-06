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

/** Elige la base del negocio activo y crea su pool de conexiones la primera vez que se usa. */
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
        String slug = TenantContext.actual()
                .orElseThrow(() -> new IllegalStateException("No hay un negocio activo en esta petición"));
        return pools.computeIfAbsent(slug, fabrica);
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
