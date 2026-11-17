package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.DatosFiscales;

// Comprobantes: el administrador de la plataforma registra con qué datos emite cada negocio.
public interface RegistrarDatosFiscalesUseCase {

    DatosFiscales registrar(String identificadorNegocio, String ruc, String razonSocial, String domicilioFiscal);
}
