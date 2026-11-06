package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import pe.agroruta.domain.model.Cotizacion;
import pe.agroruta.domain.port.in.CotizarFleteUseCase;

@RestController
public class CotizacionController {

    private final CotizarFleteUseCase cotizarFlete;

    public CotizacionController(CotizarFleteUseCase cotizarFlete) {
        this.cotizarFlete = cotizarFlete;
    }

    @GetMapping("/api/lotes/{idLote}/cotizacion")
    public CotizacionRespuesta cotizar(@PathVariable Long idLote) {
        return CotizacionRespuesta.de(cotizarFlete.cotizar(idLote));
    }

    public record CotizacionRespuesta(Long idLote, String productoLote, BigDecimal cantidadKg,
                                      String mejorDestino, List<OpcionRespuesta> opciones,
                                      List<OmitidoRespuesta> destinosSinDatos) {
        static CotizacionRespuesta de(Cotizacion cotizacion) {
            List<OpcionRespuesta> opciones = cotizacion.opciones().stream()
                    .map(opcion -> OpcionRespuesta.de(opcion, opcion == cotizacion.opciones().get(0)))
                    .toList();
            return new CotizacionRespuesta(cotizacion.lote().idLote(), cotizacion.lote().productoLote(),
                    cotizacion.lote().cantidadKg(),
                    cotizacion.mejorOpcion().map(o -> o.destino().nombreDestino()).orElse(null),
                    opciones,
                    cotizacion.omitidos().stream().map(OmitidoRespuesta::de).toList());
        }
    }

    public record OpcionRespuesta(Long idDestinoVenta, String nombreDestino, String ciudadDestino, boolean esLocal,
                                  BigDecimal precioEsperadoPorKg, BigDecimal costoFletePorKg,
                                  BigDecimal ingresoEsperado, BigDecimal costoFleteTotal,
                                  BigDecimal gananciaNeta, boolean conviene) {
        static OpcionRespuesta de(Cotizacion.Opcion opcion, boolean conviene) {
            return new OpcionRespuesta(opcion.destino().idDestinoVenta(), opcion.destino().nombreDestino(),
                    opcion.destino().ciudadDestino(), opcion.destino().esLocal(),
                    opcion.precioEsperadoPorKg().monto(), opcion.costoFletePorKg().monto(),
                    opcion.ingresoEsperado().monto(), opcion.costoFleteTotal().monto(),
                    opcion.gananciaNeta().monto(), conviene);
        }
    }

    public record OmitidoRespuesta(Long idDestinoVenta, String nombreDestino, Cotizacion.Motivo motivo) {
        static OmitidoRespuesta de(Cotizacion.Omitido omitido) {
            return new OmitidoRespuesta(omitido.destino().idDestinoVenta(), omitido.destino().nombreDestino(),
                    omitido.motivo());
        }
    }
}
