package pe.agroruta.domain.model;

import java.util.List;

/** Lo que se puede elegir en los filtros de producto y de destino. */
public record OpcionesFiltro(List<String> productos, List<OpcionDestino> destinos) {

    public record OpcionDestino(Long idDestinoVenta, String nombre) {
    }
}
