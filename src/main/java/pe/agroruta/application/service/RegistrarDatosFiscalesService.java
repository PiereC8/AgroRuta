package pe.agroruta.application.service;

import pe.agroruta.domain.model.DatosFiscales;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.port.in.RegistrarDatosFiscalesUseCase;
import pe.agroruta.domain.port.out.DatosFiscalesPort;

public class RegistrarDatosFiscalesService implements RegistrarDatosFiscalesUseCase {

    private final DatosFiscalesPort datosFiscales;

    public RegistrarDatosFiscalesService(DatosFiscalesPort datosFiscales) {
        this.datosFiscales = datosFiscales;
    }

    @Override
    public DatosFiscales registrar(String identificadorNegocio, String ruc, String razonSocial,
                                   String domicilioFiscal) {
        DatosFiscales datos = new DatosFiscales(ruc, razonSocial, domicilioFiscal);
        if (!datosFiscales.guardar(identificadorNegocio, datos)) {
            throw new RecursoNoEncontradoException("No existe el negocio " + identificadorNegocio);
        }
        return datos;
    }
}
