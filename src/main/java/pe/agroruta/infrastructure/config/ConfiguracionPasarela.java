package pe.agroruta.infrastructure.config;

/** Lo que la página de pago necesita saber. Solo contiene datos públicos: nunca el token de acceso. */
public record ConfiguracionPasarela(String modo, String llavePublica) {

    public static final String SIMULADO = "SIMULADO";
    public static final String MERCADOPAGO = "MERCADOPAGO";
}
