package pe.agroruta.domain.model;

import java.util.Map;

/** Lo que hoy tiene fijado un destino: flete por kg (null si no tiene) y precio esperado por kg de cada producto. */
public record CondicionesDestino(Dinero costoFletePorKg, Map<String, Dinero> preciosPorKg) {

    public static final CondicionesDestino VACIAS = new CondicionesDestino(null, Map.of());
}
