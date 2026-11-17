package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

// RFF-06: regla del cotizador «vender aquí o enviar», sin base de datos (RNF-11).
/** Regla del cotizador: ganancia neta = precio esperado x kg - flete x kg. Un destino local no paga flete. */
public final class CotizadorDestinos {

    private CotizadorDestinos() {
    }

    public static Cotizacion cotizar(Lote lote, BigDecimal cantidadKg, List<DestinoVenta> destinos,
                                     Map<Long, Dinero> preciosPorKg,
                                     Map<Long, Dinero> fletesPorKg) {
        List<Cotizacion.Opcion> opciones = new ArrayList<>();
        List<Cotizacion.Omitido> omitidos = new ArrayList<>();

        for (DestinoVenta destino : destinos) {
            Dinero precioPorKg = preciosPorKg.get(destino.idDestinoVenta());
            // Sin precio para el producto: el destino se informa como omitido.
            if (precioPorKg == null) {
                omitidos.add(new Cotizacion.Omitido(destino, Cotizacion.Motivo.SIN_PRECIO));
                continue;
            }
            Dinero fletePorKg = destino.esLocal() ? Dinero.CERO : fletesPorKg.get(destino.idDestinoVenta());
            if (fletePorKg == null) {
                omitidos.add(new Cotizacion.Omitido(destino, Cotizacion.Motivo.SIN_TARIFA));
                continue;
            }
            // ganancia neta = precio x kg - flete x kg
            Dinero ingreso = precioPorKg.multiplicar(cantidadKg);
            Dinero flete = fletePorKg.multiplicar(cantidadKg);
            opciones.add(new Cotizacion.Opcion(destino, precioPorKg, fletePorKg, ingreso, flete,
                    ingreso.restar(flete)));
        }

        // El primero de la lista es el que más deja (el recomendado).
        opciones.sort(Comparator.comparing(Cotizacion.Opcion::gananciaNeta, Comparator.reverseOrder())
                .thenComparing(opcion -> opcion.destino().nombreDestino()));
        return new Cotizacion(lote, cantidadKg, List.copyOf(opciones), List.copyOf(omitidos));
    }
}
