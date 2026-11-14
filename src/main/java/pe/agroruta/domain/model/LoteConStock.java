package pe.agroruta.domain.model;

import java.math.BigDecimal;

/** Un lote con lo que ya está comprometido en pedidos no cancelados; lo que queda se puede seguir vendiendo. */
public record LoteConStock(Lote lote, BigDecimal kgComprometidos) {

    public BigDecimal kgDisponibles() {
        return lote.cantidadKg().subtract(kgComprometidos);
    }
}
