package pe.agroruta.domain.model;

import java.util.List;
import java.util.Optional;

/** Resultado de comparar los destinos de un lote: precio esperado menos flete, de mayor a menor ganancia. */
public record Cotizacion(Lote lote, List<Opcion> opciones, List<Omitido> omitidos) {

    public record Opcion(DestinoVenta destino, Dinero precioEsperadoPorKg, Dinero costoFletePorKg,
                         Dinero ingresoEsperado, Dinero costoFleteTotal, Dinero gananciaNeta) {
    }

    public enum Motivo {
        SIN_PRECIO,
        SIN_TARIFA
    }

    public record Omitido(DestinoVenta destino, Motivo motivo) {
    }

    public Optional<Opcion> mejorOpcion() {
        return opciones.stream().findFirst();
    }
}
