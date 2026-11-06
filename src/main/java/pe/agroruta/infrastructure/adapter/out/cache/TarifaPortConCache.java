package pe.agroruta.infrastructure.adapter.out.cache;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.port.out.TarifaPort;
import pe.agroruta.infrastructure.config.TenantContext;

/** Proxy de caché: las tarifas cambian poco, así que se guardan unos minutos y solo se piden las que faltan. */
public class TarifaPortConCache implements TarifaPort {

    private final TarifaPort tarifas;
    private final MemoriaConExpiracion memoria;

    public TarifaPortConCache(TarifaPort tarifas, MemoriaConExpiracion memoria) {
        this.tarifas = tarifas;
        this.memoria = memoria;
    }

    @Override
    public Map<Long, Dinero> costosFletePorKg(Collection<Long> idsDestinoVenta) {
        Optional<String> negocio = TenantContext.actual();
        if (negocio.isEmpty()) {
            return tarifas.costosFletePorKg(idsDestinoVenta);
        }
        Map<Long, Dinero> resultado = new HashMap<>();
        List<Long> faltantes = new ArrayList<>();
        for (Long id : idsDestinoVenta) {
            Optional<Dinero> guardado = memoria.buscar(negocio.get(), "flete:" + id);
            if (guardado.isPresent()) {
                resultado.put(id, guardado.get());
            } else {
                faltantes.add(id);
            }
        }
        if (!faltantes.isEmpty()) {
            // Los destinos sin tarifa no se guardan: así una tarifa nueva se ve apenas existe.
            tarifas.costosFletePorKg(faltantes).forEach((id, costo) -> {
                memoria.guardar(negocio.get(), "flete:" + id, costo);
                resultado.put(id, costo);
            });
        }
        return resultado;
    }
}
