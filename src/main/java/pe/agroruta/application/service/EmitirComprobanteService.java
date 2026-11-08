package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.ReglasComprobante;
import pe.agroruta.domain.model.ResultadoEmision;
import pe.agroruta.domain.model.SeriesComprobante;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.port.in.EmitirComprobanteUseCase;
import pe.agroruta.domain.port.out.CompradorRepositoryPort;
import pe.agroruta.domain.port.out.ComprobantePort;
import pe.agroruta.domain.port.out.ComprobanteRepositoryPort;
import pe.agroruta.domain.port.out.CorrelativoPort;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.PagoRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;

public class EmitirComprobanteService implements EmitirComprobanteUseCase {

    private static final Logger REGISTRO = LoggerFactory.getLogger(EmitirComprobanteService.class);

    private final PagoRepositoryPort pagos;
    private final ComprobanteRepositoryPort comprobantes;
    private final DescargarComprobanteService.ArmadorDatosComprobante armador;
    private final CorrelativoPort correlativos;
    private final ComprobantePort emisor;
    private final SeriesComprobante series;
    private final Clock reloj;

    public EmitirComprobanteService(PagoRepositoryPort pagos, ComprobanteRepositoryPort comprobantes,
                                    LiquidacionRepositoryPort liquidaciones, PedidoRepositoryPort pedidos,
                                    CompradorRepositoryPort compradores, LoteRepositoryPort lotes,
                                    CorrelativoPort correlativos, ComprobantePort emisor, SeriesComprobante series,
                                    Clock reloj) {
        this.pagos = pagos;
        this.comprobantes = comprobantes;
        this.armador = new DescargarComprobanteService.ArmadorDatosComprobante(liquidaciones, pedidos, compradores, lotes);
        this.correlativos = correlativos;
        this.emisor = emisor;
        this.series = series;
        this.reloj = reloj;
    }

    @Override
    public Comprobante emitirPara(Long idPago) {
        Pago pago = pagos.buscarPorId(idPago)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + idPago));
        if (pago.estadoPago() != EstadoPago.APROBADO) {
            throw new pe.agroruta.domain.model.ConflictoDominioException(
                    "Solo se emite comprobante de un pago aprobado");
        }
        var existente = comprobantes.buscarPorPago(idPago);
        if (existente.isPresent()) {
            return existente.get().esPendiente() ? intentar(existente.get()) : existente.get();
        }

        TipoComprobante tipo = ReglasComprobante.tipoPara(armador.comprador(pago), pago.monto());
        String serie = series.paraTipo(tipo);
        int numero = correlativos.siguiente(serie);
        Instant ahora = reloj.instant();
        Comprobante nuevo = comprobantes.guardarNuevo(
                Comprobante.nuevo(idPago, tipo, serie, numero, LocalDate.now(reloj), ahora));
        return intentar(nuevo);
    }

    @Override
    public int reintentarPendientes(int limite) {
        List<Comprobante> pendientes = comprobantes.pendientesParaReintento(reloj.instant(), limite);
        for (Comprobante pendiente : pendientes) {
            try {
                intentar(pendiente);
            } catch (RuntimeException e) {
                REGISTRO.error("No se pudo reintentar el comprobante {}: {}", pendiente.numeroCompleto(),
                        e.getMessage());
            }
        }
        return pendientes.size();
    }

    @Override
    public Comprobante reintentar(Long idComprobante) {
        Comprobante comprobante = obtener(idComprobante);
        // También se puede reintentar uno rechazado, por ejemplo después de corregir la configuración.
        return comprobante.estadoComprobante() == EstadoComprobante.ACEPTADO ? comprobante : intentar(comprobante);
    }

    @Override
    public Comprobante consultar(Long idComprobante) {
        Comprobante comprobante = obtener(idComprobante);
        if (comprobante.estadoComprobante() == EstadoComprobante.ACEPTADO) {
            return comprobante;
        }
        try {
            ResultadoEmision resultado = emisor.consultar(comprobante.tipoComprobante(), comprobante.serie(),
                    comprobante.numero());
            Comprobante actualizado = comprobante.conResultado(resultado, reloj.instant());
            comprobantes.actualizar(actualizado);
            return actualizado;
        } catch (ServicioExternoNoDisponibleException e) {
            return comprobante;
        }
    }

    private Comprobante intentar(Comprobante comprobante) {
        Instant ahora = reloj.instant();
        Pago pago = pagos.buscarPorId(comprobante.idPago())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el pago " + comprobante.idPago()));
        DatosComprobante datos = armador.armar(comprobante, pago);
        Comprobante resultado;
        try {
            resultado = comprobante.conResultado(emisor.emitir(datos), ahora);
        } catch (ServicioExternoNoDisponibleException e) {
            REGISTRO.warn("El proveedor de comprobantes no respondió para {}: {}", comprobante.numeroCompleto(),
                    e.getMessage());
            resultado = comprobante.fallido("El proveedor de comprobantes no respondió; se reintentará", ahora);
        }
        comprobantes.actualizar(resultado);
        return resultado;
    }

    private Comprobante obtener(Long idComprobante) {
        return comprobantes.buscarPorId(idComprobante)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el comprobante " + idComprobante));
    }
}
