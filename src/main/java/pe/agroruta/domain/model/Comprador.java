package pe.agroruta.domain.model;

import java.util.regex.Pattern;

/**
 * RF-07 / RFF-04 (mantener compradores): comprador del negocio (mayorista, mercado o empresa).
 * Cómo: el constructor valida y limpia los datos; el correo es opcional y se usa para RF-14.
 */
public record Comprador(Long idComprador, String nombreComprador, TipoComprador tipoComprador,
                        String documentoComprador, String telefonoComprador, String ciudadComprador,
                        String correoComprador, boolean compradorActivo) {

    private static final Pattern FORMATO_DOCUMENTO = Pattern.compile("^(\\d{8}|\\d{11})$");
    private static final Pattern FORMATO_TELEFONO = Pattern.compile("^[0-9+() -]{6,20}$");

    public Comprador {
        if (nombreComprador == null || nombreComprador.strip().length() < 2 || nombreComprador.strip().length() > 120) {
            throw new DominioException("El nombre del comprador es obligatorio y tiene de 2 a 120 caracteres");
        }
        nombreComprador = nombreComprador.strip();
        if (tipoComprador == null) {
            throw new DominioException("El tipo de comprador es obligatorio");
        }
        if (ciudadComprador == null || ciudadComprador.strip().length() < 2 || ciudadComprador.strip().length() > 80) {
            throw new DominioException("La ciudad del comprador es obligatoria y tiene de 2 a 80 caracteres");
        }
        ciudadComprador = ciudadComprador.strip();
        documentoComprador = documentoComprador == null || documentoComprador.isBlank() ? null
                : documentoComprador.strip();
        if (documentoComprador != null && !FORMATO_DOCUMENTO.matcher(documentoComprador).matches()) {
            throw new DominioException("El documento debe ser un DNI de 8 dígitos o un RUC de 11 dígitos");
        }
        telefonoComprador = telefonoComprador == null || telefonoComprador.isBlank() ? null
                : telefonoComprador.strip();
        if (telefonoComprador != null && !FORMATO_TELEFONO.matcher(telefonoComprador).matches()) {
            throw new DominioException("El teléfono solo admite números, espacios, +, paréntesis y guion (6 a 20)");
        }
        // RF-07. Opcional: si está, al despachar le llega solo el enlace para seguir su pedido.
        correoComprador = Correo.normalizar(correoComprador);
    }

    public static Comprador nuevo(String nombreComprador, TipoComprador tipoComprador, String documentoComprador,
                                  String telefonoComprador, String ciudadComprador) {
        return nuevo(nombreComprador, tipoComprador, documentoComprador, telefonoComprador, ciudadComprador, null);
    }

    public static Comprador nuevo(String nombreComprador, TipoComprador tipoComprador, String documentoComprador,
                                  String telefonoComprador, String ciudadComprador, String correoComprador) {
        return new Comprador(null, nombreComprador, tipoComprador, documentoComprador, telefonoComprador,
                ciudadComprador, correoComprador, true);
    }

    public Comprador conId(Long nuevoIdComprador) {
        return new Comprador(nuevoIdComprador, nombreComprador, tipoComprador, documentoComprador,
                telefonoComprador, ciudadComprador, correoComprador, compradorActivo);
    }
}
