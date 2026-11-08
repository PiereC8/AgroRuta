package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.port.in.ConsultarPagosUseCase;
import pe.agroruta.domain.port.in.DescargarComprobanteUseCase;
import pe.agroruta.domain.port.in.EmitirComprobanteUseCase;

@RestController
public class PagoController {

    private final ConsultarPagosUseCase consultarPagos;
    private final EmitirComprobanteUseCase comprobantes;
    private final DescargarComprobanteUseCase descargar;

    public PagoController(ConsultarPagosUseCase consultarPagos, EmitirComprobanteUseCase comprobantes,
                          DescargarComprobanteUseCase descargar) {
        this.consultarPagos = consultarPagos;
        this.comprobantes = comprobantes;
        this.descargar = descargar;
    }

    @GetMapping("/api/pagos")
    public List<PagoRespuesta> listar(@RequestParam(defaultValue = "0") int pagina,
                                      @RequestParam(defaultValue = "50") int tamano) {
        return consultarPagos.listar(pagina, tamano).stream().map(PagoRespuesta::de).toList();
    }

    @GetMapping("/api/lotes/{idLote}/pago")
    public PagoRespuesta deLote(@PathVariable Long idLote) {
        return PagoRespuesta.de(consultarPagos.pagoDeLote(idLote));
    }

    @GetMapping("/api/pagos/{idPago}/comprobante")
    public ComprobanteRespuesta comprobanteDePago(@PathVariable Long idPago) {
        return ComprobanteRespuesta.de(consultarPagos.comprobanteDePago(idPago));
    }

    @GetMapping("/api/pagos/{idPago}/comprobante.pdf")
    public ResponseEntity<byte[]> pdfDePago(@PathVariable Long idPago) {
        return ArchivoRespuesta.descarga(descargar.pdfDePago(idPago));
    }

    @PostMapping("/api/comprobantes/{idComprobante}/reintentar")
    public ComprobanteRespuesta reintentar(@PathVariable Long idComprobante) {
        return ComprobanteRespuesta.de(comprobantes.reintentar(idComprobante));
    }

    @PostMapping("/api/comprobantes/{idComprobante}/consultar")
    public ComprobanteRespuesta consultar(@PathVariable Long idComprobante) {
        return ComprobanteRespuesta.de(comprobantes.consultar(idComprobante));
    }

    public record PagoRespuesta(Long idPago, Long idLiquidacion, Long idPedido, BigDecimal monto,
                                EstadoPago estadoPago, String mensaje, String marcaTarjeta, String ultimosCuatro,
                                String emailPagador, Instant fechaCreacion, Instant fechaActualizacion) {
        static PagoRespuesta de(Pago pago) {
            return new PagoRespuesta(pago.idPago(), pago.idLiquidacion(), pago.idPedido(), pago.monto().monto(),
                    pago.estadoPago(), pago.mensajeUsuario(), pago.marcaTarjeta(), pago.ultimosCuatro(),
                    pago.emailPagador(), pago.fechaCreacion(), pago.fechaActualizacion());
        }
    }

    public record ComprobanteRespuesta(Long idComprobante, Long idPago, TipoComprobante tipoComprobante,
                                       String numero, EstadoComprobante estadoComprobante, String enlacePdf,
                                       String enlaceXml, String mensajeSunat, int intentos, Instant proximoIntento) {
        static ComprobanteRespuesta de(Comprobante c) {
            return new ComprobanteRespuesta(c.idComprobante(), c.idPago(), c.tipoComprobante(), c.numeroCompleto(),
                    c.estadoComprobante(), c.enlacePdf(), c.enlaceXml(), c.mensajeSunat(), c.intentos(),
                    c.proximoIntento());
        }
    }
}
