package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/** Lo que se envía al proveedor para emitir el comprobante. Los productos agrícolas frescos van exonerados de IGV. */
public record DatosComprobante(TipoComprobante tipo, String serie, int numero, LocalDate fechaEmision,
                               Cliente cliente, List<Item> items, Dinero total) {

    /** tipoDocumento: "6" RUC, "1" DNI o "-" cuando no se identifica al cliente. */
    public record Cliente(String tipoDocumento, String numeroDocumento, String denominacion, String email) {
    }

    public record Item(String unidadMedida, String codigo, String descripcion, BigDecimal cantidad,
                       Dinero valorUnitario, Dinero total) {
    }
}
