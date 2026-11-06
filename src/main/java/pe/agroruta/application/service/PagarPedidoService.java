package pe.agroruta.application.service;

import java.time.Clock;
import java.util.Map;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.ReglasComprobante;
import pe.agroruta.domain.model.ResultadoCobro;
import pe.agroruta.domain.model.SesionEnlace;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.SolicitudCobro;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.EmitirComprobanteUseCase;
import pe.agroruta.domain.port.in.PagarPedidoUseCase;
import pe.agroruta.domain.port.out.CompradorRepositoryPort;
import pe.agroruta.domain.port.out.ComprobanteRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;
import pe.agroruta.domain.port.out.PagoPort;
import pe.agroruta.domain.port.out.PagoRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;

public class PagarPedidoService implements PagarPedidoUseCase {

    private static final Logger REGISTRO = LoggerFactory.getLogger(PagarPedidoService.class);

    private final EnvioRepositoryPort envios;
    private final PedidoRepositoryPort pedidos;
    private final LiquidacionRepositoryPort liquidaciones;
    private final CompradorRepositoryPort compradores;
    private final PagoRepositoryPort pagos;
    private final ComprobanteRepositoryPort comprobantes;
    private final PagoPort pasarela;
    private final EmitirComprobanteUseCase emitirComprobante;
    private final Clock reloj;

    public PagarPedidoService(EnvioRepositoryPort envios, PedidoRepositoryPort pedidos,
                              LiquidacionRepositoryPort liquidaciones, CompradorRepositoryPort compradores,
                              PagoRepositoryPort pagos, ComprobanteRepositoryPort comprobantes, PagoPort pasarela,
                              EmitirComprobanteUseCase emitirComprobante, Clock reloj) {
        this.envios = envios;
        this.pedidos = pedidos;
        this.liquidaciones = liquidaciones;
        this.compradores = compradores;
        this.pagos = pagos;
        this.comprobantes = comprobantes;
        this.pasarela = pasarela;
        this.emitirComprobante = emitirComprobante;
        this.reloj = reloj;
    }

    @Override
    public ResultadoPago pagar(Comando comando) {
        SesionEnlace sesion = exigirComprador(comando.sesion());
        Contexto contexto = contexto(sesion);

        Optional<Pago> repetido = pagos.buscarPorClave(comando.claveIdempotencia());
        if (repetido.isPresent()) {
            if (!repetido.get().idLiquidacion().equals(contexto.liquidacion().idLiquidacion())) {
                throw new ConflictoDominioException("Esa clave de operación ya se usó en otro pedido");
            }
            return resultado(repetido.get());
        }

        if (contexto.envio().estadoEnvio() != EstadoEnvio.ENTREGADO) {
            throw new ConflictoDominioException("El pedido todavía no fue entregado, no se puede pagar");
        }
        Long idLiquidacion = contexto.liquidacion().idLiquidacion();
        if (pagos.existeAprobadoDe(idLiquidacion)) {
            throw new ConflictoDominioException("Este pedido ya está pagado");
        }
        if (pagos.existePendienteDe(idLiquidacion)) {
            throw new ConflictoDominioException("Estamos confirmando un pago anterior. Espera unos minutos antes de pagar de nuevo");
        }
        if (comando.tokenTarjeta() == null || comando.tokenTarjeta().isBlank() || comando.tokenTarjeta().length() > 100) {
            throw new DominioException("Falta el token de la tarjeta");
        }
        if (comando.metodoPago() != null && !comando.metodoPago().matches("[a-z_]{2,30}")) {
            throw new DominioException("El medio de pago no es válido");
        }
        if (comando.emisor() != null && !comando.emisor().matches("\\d{1,10}")) {
            throw new DominioException("El emisor de la tarjeta no es válido");
        }
        // Se valida antes de cobrar: no tiene sentido cobrar si luego no se puede emitir el comprobante.
        ReglasComprobante.tipoPara(contexto.comprador(), contexto.liquidacion().ingresoReal());

        Pago pago = pagos.guardarNuevo(
                Pago.nuevo(contexto.liquidacion(), comando.email(), comando.claveIdempotencia(), reloj.instant()));
        SolicitudCobro solicitud = new SolicitudCobro(pago.monto(), pago.emailPagador(), comando.tokenTarjeta(),
                pago.claveIdempotencia(), "AgroRuta - pedido " + contexto.pedido().idPedido(),
                Map.of("negocio", sesion.identificadorNegocio(), "idPago", String.valueOf(pago.idPago())),
                comando.metodoPago(), comando.emisor());

        ResultadoCobro resultadoCobro;
        try {
            resultadoCobro = pasarela.cobrar(solicitud);
        } catch (ServicioExternoNoDisponibleException e) {
            // No se sabe si cobró. Queda PENDIENTE y la notificación de la pasarela lo confirmará.
            REGISTRO.warn("La pasarela no respondió al cobrar el pago {}: {}", pago.idPago(), e.getMessage());
            return resultado(pago);
        }

        Pago actualizado = pago.aplicar(resultadoCobro, reloj.instant());
        if (actualizado != pago) {
            pagos.actualizar(actualizado);
        }
        if (actualizado.estadoPago() == EstadoPago.APROBADO) {
            emitirSinFallar(actualizado.idPago());
        }
        return resultado(actualizado);
    }

    @Override
    public EstadoPagoPublico estado(SesionEnlace sesion) {
        exigirComprador(sesion);
        Envio envio = envios.buscarPorId(sesion.idEnvio())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + sesion.idEnvio()));
        Optional<Liquidacion> liquidacion = liquidaciones.buscarPorLote(envio.idLote());
        if (liquidacion.isEmpty()) {
            return new EstadoPagoPublico(envio.estadoEnvio(), false, Dinero.CERO, descripcion(envio.idPedido()), null,
                    null);
        }
        Long idLiquidacion = liquidacion.get().idLiquidacion();
        Optional<Pago> ultimo = pagos.ultimoDeLiquidacion(idLiquidacion);
        boolean pagable = envio.estadoEnvio() == EstadoEnvio.ENTREGADO && liquidacion.get().ingresoReal().esPositivo()
                && !pagos.existeAprobadoDe(idLiquidacion) && !pagos.existePendienteDe(idLiquidacion);
        Comprobante comprobante = ultimo.filter(p -> p.estadoPago() == EstadoPago.APROBADO)
                .flatMap(p -> comprobantes.buscarPorPago(p.idPago())).orElse(null);
        return new EstadoPagoPublico(envio.estadoEnvio(), pagable, liquidacion.get().ingresoReal(),
                descripcion(envio.idPedido()), ultimo.orElse(null), comprobante);
    }

    private ResultadoPago resultado(Pago pago) {
        Comprobante comprobante = pago.estadoPago() == EstadoPago.APROBADO
                ? comprobantes.buscarPorPago(pago.idPago()).orElse(null) : null;
        String mensaje = switch (pago.estadoPago()) {
            case APROBADO -> "¡Pago recibido! Gracias.";
            case RECHAZADO -> pago.mensajeUsuario();
            case PENDIENTE -> "Estamos confirmando tu pago. No pagues de nuevo: vuelve a revisar en unos minutos.";
        };
        return new ResultadoPago(pago.idPago(), pago.estadoPago(), mensaje, pago.monto(), comprobante);
    }

    private void emitirSinFallar(Long idPago) {
        try {
            emitirComprobante.emitirPara(idPago);
        } catch (RuntimeException e) {
            // El cobro ya ocurrió: un problema con el comprobante no debe deshacerlo ni mostrarse como error de pago.
            REGISTRO.error("No se pudo emitir el comprobante del pago {}: {}", idPago, e.getMessage());
        }
    }

    private String descripcion(Long idPedido) {
        return "Pedido " + idPedido;
    }

    private Contexto contexto(SesionEnlace sesion) {
        Envio envio = envios.buscarPorId(sesion.idEnvio())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + sesion.idEnvio()));
        Pedido pedido = pedidos.buscarPorId(envio.idPedido())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pedido " + envio.idPedido()));
        Liquidacion liquidacion = liquidaciones.buscarPorLote(envio.idLote())
                .orElseThrow(() -> new ConflictoDominioException("El pedido todavía no tiene liquidación"));
        Comprador comprador = compradores.buscarPorId(pedido.idComprador()).orElseThrow(
                () -> new RecursoNoEncontradoException("No existe el comprador " + pedido.idComprador()));
        return new Contexto(envio, pedido, liquidacion, comprador);
    }

    private static SesionEnlace exigirComprador(SesionEnlace sesion) {
        if (sesion == null || sesion.tipoEnlace() != TipoEnlace.COMPRADOR) {
            throw new DominioException("Este enlace no permite pagar");
        }
        return sesion;
    }

    private record Contexto(Envio envio, Pedido pedido, Liquidacion liquidacion, Comprador comprador) {
    }
}
