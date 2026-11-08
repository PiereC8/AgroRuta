package pe.agroruta.infrastructure.adapter.out.comprobante;

/** Quien emite los comprobantes. En el ambiente beta de SUNAT se puede usar el RUC de prueba 20000000001. */
public record DatosEmisor(String ruc, String razonSocial, String nombreComercial, String domicilioFiscal,
                          String ubigeo, String distrito, String provincia, String departamento) {
}
