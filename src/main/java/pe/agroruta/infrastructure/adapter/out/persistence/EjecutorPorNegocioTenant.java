package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.function.Supplier;

import org.springframework.stereotype.Component;

import pe.agroruta.domain.port.out.EjecutorPorNegocioPort;
import pe.agroruta.infrastructure.config.TenantContext;

@Component
public class EjecutorPorNegocioTenant implements EjecutorPorNegocioPort {

    @Override
    public <T> T ejecutarEn(String identificadorNegocio, Supplier<T> accion) {
        return TenantContext.ejecutarEn(identificadorNegocio, accion);
    }
}
