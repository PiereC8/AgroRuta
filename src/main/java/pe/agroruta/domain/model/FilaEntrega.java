package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.time.Instant;

/** Una entrega liquidada, con todo lo que un reporte necesita saber de ella. */
public record FilaEntrega(Long idLiquidacion, Long idPedido, Instant fechaEntrega, String comprador, String producto,
                          String destino, BigDecimal kgDespachados, BigDecimal kgRecibidos, BigDecimal mermaKg,
                          BigDecimal rechazoKg, Dinero ingresoReal, Dinero fleteTotal, Dinero gananciaReal,
                          Dinero gananciaEsperada, Dinero perdidaMermaRechazo, boolean cobrada) {
}
