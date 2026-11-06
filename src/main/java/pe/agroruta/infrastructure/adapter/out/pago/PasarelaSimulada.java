package pe.agroruta.infrastructure.adapter.out.pago;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.SolicitudCobro;
import pe.agroruta.domain.port.out.PagoPort;

/**
 * Pasarela de mentira para desarrollar y demostrar sin cuenta. Se elige con el token que manda la página:
 * sim_aprobada, sim_sin_fondos, sim_vencida o sim_sin_respuesta (cobra pero no contesta).
 */
public class PasarelaSimulada implements PagoPort {

    public static final String APROBADA = "sim_aprobada";
    public static final String SIN_FONDOS = "sim_sin_fondos";
    public static final String VENCIDA = "sim_vencida";
    public static final String SIN_RESPUESTA = "sim_sin_respuesta";

    private final Map<String, ResultadoCobro> cargos = new ConcurrentHashMap<>();

    @Override
    public ResultadoCobro cobrar(SolicitudCobro solicitud) {
        // Con la forma de un cargo real (chr_xxx_...) para que la notificación de la pasarela también se pueda probar.
        String idCargo = "chr_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 16);
        Map<String, String> metadatos = solicitud.metadatos();
        String token = solicitud.tokenTarjeta();
        if (SIN_FONDOS.equals(token)) {
            return guardar(ResultadoCobro.rechazado(idCargo, "fondos_insuficientes",
                    "La tarjeta no tiene fondos suficientes", metadatos));
        }
        if (VENCIDA.equals(token)) {
            return guardar(ResultadoCobro.rechazado(idCargo, "tarjeta_vencida", "La tarjeta está vencida", metadatos));
        }
        if (APROBADA.equals(token) || SIN_RESPUESTA.equals(token)) {
            ResultadoCobro aprobado = guardar(ResultadoCobro.aprobado(idCargo, "Visa", "4242", metadatos));
            if (SIN_RESPUESTA.equals(token)) {
                throw new ServicioExternoNoDisponibleException("La pasarela simulada no respondió (el cargo " + idCargo
                        + " sí se hizo)");
            }
            return aprobado;
        }
        return guardar(ResultadoCobro.rechazado(idCargo, "datos_invalidos", "Los datos de la tarjeta no son válidos",
                metadatos));
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
