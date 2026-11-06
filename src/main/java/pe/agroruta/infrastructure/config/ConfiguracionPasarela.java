package pe.agroruta.infrastructure.config;

/** Lo que la página de pago necesita saber. Solo contiene datos públicos: nunca la llave secreta. */
public record ConfiguracionPasarela(String modo, String llavePublica, String rsaId, String rsaPublica) {

    public static final String SIMULADO = "SIMULADO";
    public static final String CULQI = "CULQI";
}
