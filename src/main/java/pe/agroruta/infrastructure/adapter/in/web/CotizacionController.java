package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

import com.fasterxml.jackson.annotation.JsonInclude;

import pe.agroruta.domain.model.Cotizacion;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.CotizarFleteUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

@RestController
public class CotizacionController {

    private final CotizarFleteUseCase cotizarFlete;

    public CotizacionController(CotizarFleteUseCase cotizarFlete) {
        this.cotizarFlete = cotizarFlete;
    }

    @GetMapping("/api/lotes/{idLote}/cotizacion")
    public CotizacionRespuesta cotizar(@PathVariable Long idLote, @AuthenticationPrincipal DatosToken usuario) {
        boolean dueno = usuario != null && usuario.rolUsuario() == Rol.DUENO;
        return CotizacionRespuesta.de(cotizarFlete.cotizar(idLote), dueno);
    }

    public record CotizacionRespuesta(Long idLote, String productoLote, BigDecimal cantidadKg,
                                      String mejorDestino, List<OpcionRespuesta> opciones,
                                      List<OmitidoRespuesta> destinosSinDatos) {
        static CotizacionRespuesta de(Cotizacion cotizacion, boolean dueno) {
            List<OpcionRespuesta> opciones = cotizacion.opciones().stream()
                    .map(opcion -> OpcionRespuesta.de(opcion, opcion == cotizacion.opciones().get(0), dueno))
                    .toList();
            return new CotizacionRespuesta(cotizacion.lote().idLote(), cotizacion.lote().productoLote(),
                    cotizacion.lote().cantidadKg(),
                    cotizacion.mejorOpcion().map(o -> o.destino().nombreDestino()).orElse(null),
                    opciones,
                    cotizacion.omitidos().stream().map(OmitidoRespuesta::de).toList());
        }
    }

    /** El Operador ve a dónde conviene enviar, pero no el flete ni la ganancia: esos campos faltan por completo. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record OpcionRespuesta(Long idDestinoVenta, String nombreDestino, String ciudadDestino, boolean esLocal,
                                  BigDecimal precioEsperadoPorKg, BigDecimal costoFletePorKg,
                                  BigDecimal ingresoEsperado, BigDecimal costoFleteTotal,
                                  BigDecimal gananciaNeta, boolean conviene) {
        static OpcionRespuesta de(Cotizacion.Opcion opcion, boolean conviene, boolean dueno) {
            return new OpcionRespuesta(opcion.destino().idDestinoVenta(), opcion.destino().nombreDestino(),
                    opcion.destino().ciudadDestino(), opcion.destino().esLocal(),
                    opcion.precioEsperadoPorKg().monto(), dueno ? opcion.costoFletePorKg().monto() : null,
                    opcion.ingresoEsperado().monto(), dueno ? opcion.costoFleteTotal().monto() : null,
                    dueno ? opcion.gananciaNeta().monto() : null, conviene);
        }
    }

    public record OmitidoRespuesta(Long idDestinoVenta, String nombreDestino, Cotizacion.Motivo motivo) {
        static OmitidoRespuesta de(Cotizacion.Omitido omitido) {
            return new OmitidoRespuesta(omitido.destino().idDestinoVenta(), omitido.destino().nombreDestino(),
                    omitido.motivo());
        }
    }
}
