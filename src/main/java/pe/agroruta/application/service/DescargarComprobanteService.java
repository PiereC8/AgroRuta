package pe.agroruta.application.service;

import pe.agroruta.domain.model.Archivo;
import pe.agroruta.domain.model.ReglasComprobante;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.LoteNoEncontradoException;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.Comprador;
import java.util.List;
import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.ComprobanteImpreso;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.DescargarComprobanteUseCase;
import pe.agroruta.domain.port.out.CompradorRepositoryPort;
import pe.agroruta.domain.port.out.ComprobanteRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.PagoRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.RepresentacionComprobantePort;

public class DescargarComprobanteService implements DescargarComprobanteUseCase {

    private final PagoRepositoryPort pagos;
    private final ComprobanteRepositoryPort comprobantes;
    private final EnvioRepositoryPort envios;
    private final LiquidacionRepositoryPort liquidaciones;
    private final ArmadorDatosComprobante armador;
    private final RepresentacionComprobantePort representacion;

    public DescargarComprobanteService(PagoRepositoryPort pagos, ComprobanteRepositoryPort comprobantes,
                                       EnvioRepositoryPort envios, LiquidacionRepositoryPort liquidaciones,
                                       PedidoRepositoryPort pedidos, CompradorRepositoryPort compradores,
                                       LoteRepositoryPort lotes, RepresentacionComprobantePort representacion) {
        this.pagos = pagos;
        this.comprobantes = comprobantes;
        this.envios = envios;
        this.liquidaciones = liquidaciones;
        this.armador = new ArmadorDatosComprobante(liquidaciones, pedidos, compradores, lotes);
        this.representacion = representacion;
    }

    @Override
    public Archivo pdfDePago(Long idPago) {
        Pago pago = pagos.buscarPorId(idPago)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + idPago));
        return pdf(pago);
    }

    @Override
    public Archivo pdfDelComprador(SesionEnlace sesion) {
        if (sesion == null || sesion.tipoEnlace() != TipoEnlace.COMPRADOR) {
            throw new DominioException("Este enlace no permite ver el comprobante");
        }
        Envio envio = envios.buscarPorId(sesion.idEnvio())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + sesion.idEnvio()));
        Liquidacion liquidacion = liquidaciones.buscarPorLote(envio.idLote())
                .orElseThrow(() -> new RecursoNoEncontradoException("Este pedido todavía no tiene comprobante"));
        Pago pago = pagos.ultimoDeLiquidacion(liquidacion.idLiquidacion())
                .filter(p -> p.estadoPago() == EstadoPago.APROBADO)
                .orElseThrow(() -> new RecursoNoEncontradoException("Este pedido todavía no tiene comprobante"));
        return pdf(pago);
    }

    private Archivo pdf(Pago pago) {
        Comprobante comprobante = comprobantes.buscarPorPago(pago.idPago()).orElseThrow(
                () -> new RecursoNoEncontradoException("El pago " + pago.idPago() + " todavía no tiene comprobante"));
        // Solo se imprime lo que SUNAT recibió: un PDF de un comprobante pendiente o rechazado confundiría al cliente.
        if (comprobante.estadoComprobante() != EstadoComprobante.ACEPTADO
                && comprobante.estadoComprobante() != EstadoComprobante.OBSERVADO) {
            throw new ConflictoDominioException("El comprobante " + comprobante.numeroCompleto()
                    + " todavía no fue aceptado por SUNAT");
        }
        byte[] contenido = representacion.pdf(new ComprobanteImpreso(comprobante, armador.armar(comprobante, pago)));
        String tipo = comprobante.tipoComprobante() == TipoComprobante.FACTURA ? "factura" : "boleta";
        return new Archivo(tipo + "-" + comprobante.numeroCompleto() + ".pdf", "application/pdf", contenido);
    }

    /**
     * Junta lo que va en un comprobante (cliente, producto, cantidad y total) a partir del pago. Lo usan la emisión y la
     * representación impresa, para que el PDF diga exactamente lo mismo que se envió a SUNAT.
     */
    static final class ArmadorDatosComprobante {

        private final LiquidacionRepositoryPort liquidaciones;
        private final PedidoRepositoryPort pedidos;
        private final CompradorRepositoryPort compradores;
        private final LoteRepositoryPort lotes;

        ArmadorDatosComprobante(LiquidacionRepositoryPort liquidaciones, PedidoRepositoryPort pedidos,
                                CompradorRepositoryPort compradores, LoteRepositoryPort lotes) {
            this.liquidaciones = liquidaciones;
            this.pedidos = pedidos;
            this.compradores = compradores;
            this.lotes = lotes;
        }

        Comprador comprador(Pago pago) {
            Pedido pedido = pedidos.buscarPorId(pago.idPedido())
                    .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido " + pago.idPedido()));
            return compradores.buscarPorId(pedido.idComprador()).orElseThrow(
                    () -> new RecursoNoEncontradoException("No existe el comprador " + pedido.idComprador()));
        }

        DatosComprobante armar(Comprobante comprobante, Pago pago) {
            Liquidacion liquidacion = liquidaciones.buscarPorId(pago.idLiquidacion()).orElseThrow(
                    () -> new RecursoNoEncontradoException("No existe la liquidación " + pago.idLiquidacion()));
            Lote lote = lotes.buscarPorId(liquidacion.idLote())
                    .orElseThrow(() -> new LoteNoEncontradoException(liquidacion.idLote()));
            DatosComprobante.Item item = new DatosComprobante.Item("KGM", "PROD-" + lote.idLote(), lote.productoLote(),
                    liquidacion.cantidadRecibidaKg(), liquidacion.precioAcordadoPorKg(), liquidacion.ingresoReal());
            return new DatosComprobante(comprobante.tipoComprobante(), comprobante.serie(), comprobante.numero(),
                    comprobante.fechaEmision(), ReglasComprobante.clienteDe(comprador(pago), pago.emailPagador()),
                    List.of(item), pago.monto());
        }
    }
}
