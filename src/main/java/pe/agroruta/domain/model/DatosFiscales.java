package pe.agroruta.domain.model;

import java.util.regex.Pattern;

/** Con qué datos emite un negocio sus comprobantes: RUC, razón social y domicilio fiscal. */
public record DatosFiscales(String ruc, String razonSocial, String domicilioFiscal) {

    private static final Pattern FORMATO_RUC = Pattern.compile("^(10|15|17|20)\\d{9}$");

    public DatosFiscales {
        ruc = ruc == null ? null : ruc.strip();
        razonSocial = razonSocial == null ? null : razonSocial.strip();
        domicilioFiscal = domicilioFiscal == null ? null : domicilioFiscal.strip();
        if (ruc == null || !FORMATO_RUC.matcher(ruc).matches()) {
            throw new DominioException("El RUC debe tener 11 dígitos y empezar con 10, 15, 17 o 20");
        }
        if (razonSocial == null || razonSocial.isEmpty() || razonSocial.length() > 150) {
            throw new DominioException("La razón social es obligatoria y tiene hasta 150 caracteres");
        }
        if (domicilioFiscal == null || domicilioFiscal.isEmpty() || domicilioFiscal.length() > 200) {
            throw new DominioException("El domicilio fiscal es obligatorio y tiene hasta 200 caracteres");
        }
    }
}
