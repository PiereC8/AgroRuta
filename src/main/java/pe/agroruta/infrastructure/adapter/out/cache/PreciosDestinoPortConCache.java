package pe.agroruta.infrastructure.adapter.out.cache;

import java.util.Map;
import java.util.Optional;

import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.port.out.PreciosDestinoPort;
import pe.agroruta.infrastructure.config.TenantContext;

/** Proxy de caché de los precios. Al guardar un precio o una tarifa se borra la memoria de ese negocio. */
public class PreciosDestinoPortConCache implements PreciosDestinoPort {

    private final PreciosDestinoPort precios;
    private final MemoriaConExpiracion memoria;

    public PreciosDestinoPortConCache(PreciosDestinoPort precios, MemoriaConExpiracion memoria) {
        this.precios = precios;
        this.memoria = memoria;
    }

    @Override
    public void guardarPrecioEsperado(Long idDestinoVenta, String productoLote, Dinero precioPorKg) {
        precios.guardarPrecioEsperado(idDestinoVenta, productoLote, precioPorKg);
        TenantContext.actual().ifPresent(memoria::borrarNegocio);
    }

    @Override
    public Map<Long, Dinero> preciosEsperadosPorKg(String productoLote) {
        Optional<String> negocio = TenantContext.actual();
        if (negocio.isEmpty()) {
            return precios.preciosEsperadosPorKg(productoLote);
        }
        String clave = "precio:" + productoLote;
        Optional<Map<Long, Dinero>> guardado = memoria.buscar(negocio.get(), clave);
        if (guardado.isPresent()) {
            return guardado.get();
        }
        Map<Long, Dinero> consultado = Map.copyOf(precios.preciosEsperadosPorKg(productoLote));
        memoria.guardar(negocio.get(), clave, consultado);
        return consultado;
    }

    @Override
    public void guardarTarifaFlete(Long idDestinoVenta, Dinero costoFletePorKg) {
        precios.guardarTarifaFlete(idDestinoVenta, costoFletePorKg);
        TenantContext.actual().ifPresent(memoria::borrarNegocio);
    }
}
