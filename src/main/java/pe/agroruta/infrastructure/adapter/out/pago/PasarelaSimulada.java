package pe.agroruta.infrastructure.adapter.out.pago;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.SolicitudCobro;
import pe.agroruta.domain.port.out.PagoPort;

/**
 * Pasarela de mentira para desarrollar y demostrar sin cuenta ni dinero real. La página simulada valida la tarjeta
 * en el navegador (el número nunca viaja) y manda un token con la forma
 * {@code sim_<resultado>[_<marca>[_<últimos 4>]]}, por ejemplo {@code sim_aprobada_visa_4242} o
 * {@code sim_aprobada_paypal}. Resultados: aprobada, sin_fondos, vencida y sin_respuesta (cobra pero no contesta).
 */
public class PasarelaSimulada implements PagoPort {

    public static final String APROBADA = "sim_aprobada";
    public static final String SIN_FONDOS = "sim_sin_fondos";
    public static final String VENCIDA = "sim_vencida";
    public static final String SIN_RESPUESTA = "sim_sin_respuesta";

    private static final Pattern FORMATO = Pattern
            .compile("^sim_(aprobada|sin_fondos|vencida|sin_respuesta)(?:_(visa|mastercard|amex|diners|paypal)(?:_(\\d{4}))?)?$");

    /** Token del medio «pago con código»: no es una tarjeta, se genera un CIP que se paga luego en un agente. */
    public static final String PAGO_EFECTIVO = "sim_pagoefectivo";
    private static final String MEDIO_PAGO_EFECTIVO = "PagoEfectivo";
    private static final Duration VIGENCIA_DEL_CODIGO = Duration.ofHours(48);
    private static final ZoneId LIMA = ZoneId.of("America/Lima");
    private static final DateTimeFormatter FECHA_VENCIMIENTO = DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm")
            .withZone(LIMA);

    private final Map<String, ResultadoCobro> cargos = new ConcurrentHashMap<>();
    private final Clock reloj;
    private final SecureRandom azar = new SecureRandom();

    public PasarelaSimulada() {
        this(Clock.system(LIMA));
    }

    public PasarelaSimulada(Clock reloj) {
        this.reloj = reloj;
    }

    @Override
    public ResultadoCobro cobrar(SolicitudCobro solicitud) {
        // Con la forma de un cargo real (chr_xxx_...) para que la notificación de la pasarela también se pueda probar.
        String idCargo = "chr_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, String> metadatos = solicitud.metadatos();
        if (PAGO_EFECTIVO.equals(solicitud.tokenTarjeta())) {
            return guardar(generarCodigo(metadatos));
        }
        Matcher token = FORMATO.matcher(solicitud.tokenTarjeta() == null ? "" : solicitud.tokenTarjeta());
        if (!token.matches()) {
            return guardar(ResultadoCobro.rechazado(idCargo, "datos_invalidos",
                    "Los datos de la tarjeta no son válidos", metadatos));
        }
        String resultado = token.group(1);
        String marca = token.group(2) == null ? "Visa" : nombreDeMarca(token.group(2));
        String ultimosCuatro = token.group(2) == null ? "4242" : token.group(3);
        if ("sin_fondos".equals(resultado)) {
            return guardar(ResultadoCobro.rechazado(idCargo, "fondos_insuficientes",
                    "La tarjeta no tiene fondos suficientes", metadatos));
        }
        if ("vencida".equals(resultado)) {
            return guardar(ResultadoCobro.rechazado(idCargo, "tarjeta_vencida", "La tarjeta está vencida", metadatos));
        }
        ResultadoCobro aprobado = guardar(ResultadoCobro.aprobado(idCargo, marca, ultimosCuatro, metadatos));
        if ("sin_respuesta".equals(resultado)) {
            throw new ServicioExternoNoDisponibleException("La pasarela simulada no respondió (el cargo " + idCargo
                    + " sí se hizo)");
        }
        return aprobado;
    }

    private ResultadoCobro generarCodigo(Map<String, String> metadatos) {
        String cip = String.format("%012d", azar.nextLong(1_000_000_000_000L));
        String vence = FECHA_VENCIMIENTO.format(reloj.instant().plus(VIGENCIA_DEL_CODIGO));
        String mensaje = "Tu código de pago (CIP) es " + cip + ". Págalo en un agente, una bodega o por banca por "
                + "internet hasta el " + vence + ". Cuando lo pagues, tu pedido figurará como pagado.";
        return ResultadoCobro.pendiente("cip_sim_" + cip, ResultadoCobro.CIP_GENERADO, mensaje, MEDIO_PAGO_EFECTIVO,
                metadatos);
    }

    @Override
    public boolean simularPagoEnAgente(String idCargo) {
        ResultadoCobro cargo = cargos.get(idCargo);
        if (cargo == null || cargo.estado() != ResultadoCobro.Estado.PENDIENTE
                || !ResultadoCobro.CIP_GENERADO.equals(cargo.codigoRespuesta())) {
            return false;
        }
        cargos.put(idCargo, ResultadoCobro.aprobado(idCargo, MEDIO_PAGO_EFECTIVO, null, cargo.metadatos()));
        return true;
    }

    private static String nombreDeMarca(String marca) {
        return switch (marca) {
            case "visa" -> "Visa";
            case "mastercard" -> "Mastercard";
            case "amex" -> "American Express";
            case "diners" -> "Diners";
            default -> "PayPal";
        };
    }

    @Override
    public Optional<ResultadoCobro> consultarCargo(String idCargo) {
        return Optional.ofNullable(cargos.get(idCargo));
    }

    private ResultadoCobro guardar(ResultadoCobro resultado) {
        cargos.put(resultado.idCargo(), resultado);
        return resultado;
    }
}
