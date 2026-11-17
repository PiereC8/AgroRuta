package pe.agroruta.infrastructure.adapter.out.comprobante;

import java.util.function.Supplier;

import pe.agroruta.domain.port.out.DatosFiscalesPort;
import pe.agroruta.infrastructure.config.TenantContext;

/**
 * El emisor del negocio actual: su RUC, razón social y domicilio fiscal. Si el negocio no los tiene (o no hay negocio
 * en curso), se usan los de configuración (EMISOR_*), que quedan solo como respaldo para la demo. Ubigeo, distrito,
 * provincia y departamento siguen saliendo de la configuración.
 */
public class EmisorPorNegocio implements Supplier<DatosEmisor> {

    private final DatosFiscalesPort datosFiscales;
    private final DatosEmisor respaldo;

    public EmisorPorNegocio(DatosFiscalesPort datosFiscales, DatosEmisor respaldo) {
        this.datosFiscales = datosFiscales;
        this.respaldo = respaldo;
    }

    @Override
    public DatosEmisor get() {
        return TenantContext.actual().flatMap(datosFiscales::buscar)
                .map(d -> new DatosEmisor(d.ruc(), d.razonSocial(), d.razonSocial(), d.domicilioFiscal(),
                        respaldo.ubigeo(), respaldo.distrito(), respaldo.provincia(), respaldo.departamento()))
                .orElse(respaldo);
    }
}
