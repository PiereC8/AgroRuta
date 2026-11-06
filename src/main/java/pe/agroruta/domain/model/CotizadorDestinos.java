package pe.agroruta.domain.model;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

/** Regla del cotizador: ganancia neta = precio esperado x kg - flete x kg. Un destino local no paga flete. */
public final class CotizadorDestinos {

    private CotizadorDestinos() {
    }

    public static Cotizacion cotizar(Lote lote, List<DestinoVenta> destinos, Map<Long, Dinero> preciosPorKg,
                                     Map<Long, Dinero> fletesPorKg) {
        List<Cotizacion.Opcion> opciones = new ArrayList<>();
        List<Cotizacion.Omitido> omitidos = new ArrayList<>();

        for (DestinoVenta destino : destinos) {
            Dinero precioPorKg = preciosPorKg.get(destino.idDestinoVenta());
            if (precioPorKg == null) {
                omitidos.add(new Cotizacion.Omitido(destino, Cotizacion.Motivo.SIN_PRECIO));
                continue;
            }
            Dinero fletePorKg = destino.esLocal() ? Dinero.CERO : fletesPorKg.get(destino.idDestinoVenta());
            if (fletePorKg == null) {
                omitidos.add(new Cotizacion.Omitido(destino, Cotizacion.Motivo.SIN_TARIFA));
                continue;
            }
            Dinero ingreso = precioPorKg.multiplicar(lote.cantidadKg());
            Dinero flete = fletePorKg.multiplicar(lote.cantidadKg());
            opciones.add(new Cotizacion.Opcion(destino, precioPorKg, fletePorKg, ingreso, flete,
                    ingreso.restar(flete)));
        }

        opciones.sort(Comparator.comparing(Cotizacion.Opcion::gananciaNeta, Comparator.reverseOrder())
                .thenComparing(opcion -> opcion.destino().nombreDestino()));
        return new Cotizacion(lote, List.copyOf(opciones), List.copyOf(omitidos));
    }
}
