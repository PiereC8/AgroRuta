package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/** @param cantidadKg los kg que se cotizan: lo que queda disponible del lote */
public record Cotizacion(Lote lote, BigDecimal cantidadKg, List<Opcion> opciones, List<Omitido> omitidos) {

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
