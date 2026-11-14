package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import pe.agroruta.domain.model.HechoDelNegocio.AlertaDeEnvio;
import pe.agroruta.domain.model.HechoDelNegocio.CobroVencido;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteAtascado;
import pe.agroruta.domain.model.HechoDelNegocio.ComprobanteRechazado;
import pe.agroruta.domain.model.HechoDelNegocio.EntregaRegistrada;
import pe.agroruta.domain.model.HechoDelNegocio.PagoRecibido;
import pe.agroruta.domain.model.HechoDelNegocio.ResumenSemanal;

/** Decide qué hechos merecen un correo, a qué roles y qué dicen. Sin tocar la base ni el correo. */
public record PoliticaAvisos(BigDecimal mermaAltaPorcentaje) {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);

    public List<Aviso> avisosDe(HechoDelNegocio hecho) {
        return switch (hecho) {
            case EntregaRegistrada e -> deEntrega(e);
            case PagoRecibido p -> List.of(new Aviso("pago-recibido:" + p.idPago(), TipoAviso.PAGO_RECIBIDO,
                    "Pago recibido: " + soles(p.monto()),
                    p.comprador() + " pagó " + soles(p.monto()) + " del pedido " + p.idPedido() + "."));
            case ComprobanteRechazado c -> List.of(new Aviso("comprobante-rechazado:" + c.idComprobante(),
                    TipoAviso.COMPROBANTE_RECHAZADO, "No se pudo emitir el comprobante " + c.numero(),
                    "El comprobante " + c.numero() + " fue rechazado: " + c.mensaje()
                            + ". Revisa los datos del comprador y vuelve a emitirlo."));
            case ComprobanteAtascado c -> List.of(new Aviso("comprobante-atascado:" + c.idComprobante(),
                    TipoAviso.COMPROBANTE_ATASCADO, "El comprobante " + c.numero() + " sigue pendiente",
                    "El comprobante " + c.numero() + " lleva " + c.horas()
                            + " horas sin que SUNAT lo acepte. Entra a Pagos para reintentar el envío."));
            case CobroVencido c -> List.of(new Aviso("cobro-vencido:" + c.idLiquidacion(), TipoAviso.COBRO_VENCIDO,
                    "Cobro pendiente de " + c.comprador(),
                    c.comprador() + " debe " + soles(c.monto()) + " desde hace " + c.dias()
                            + " días. Aún no hay un pago aprobado."));
            case AlertaDeEnvio a -> deAlerta(a);
            case ResumenSemanal r -> List.of(deResumen(r));
        };
    }

    private List<Aviso> deEntrega(EntregaRegistrada e) {
        List<Aviso> avisos = new ArrayList<>();
        String detalle = e.comprador() + ", " + e.producto() + " hacia " + e.destino();
        if (e.gananciaReal().esNegativo()) {
            Dinero perdida = new Dinero(e.gananciaReal().monto().negate());
            avisos.add(new Aviso("entrega-con-perdida:" + e.idLiquidacion(), TipoAviso.ENTREGA_CON_PERDIDA,
                    "Entrega con pérdida de " + soles(perdida),
                    "La entrega de " + detalle + " dejó una pérdida de " + soles(perdida) + ". Se perdieron "
                            + soles(e.perdidaMermaRechazo()) + " por merma y rechazo."));
        }
        BigDecimal porcentaje = e.kgDespachados().signum() == 0 ? BigDecimal.ZERO
                : e.mermaKg().add(e.rechazoKg()).multiply(CIEN).divide(e.kgDespachados(), 1, RoundingMode.HALF_UP);
        if (porcentaje.compareTo(mermaAltaPorcentaje) > 0) {
            avisos.add(new Aviso("merma-alta:" + e.idLiquidacion(), TipoAviso.MERMA_ALTA,
                    "Merma alta: " + limpio(porcentaje) + " % en una entrega",
                    "En la entrega de " + detalle + " se perdió el " + limpio(porcentaje) + " % de lo despachado ("
                            + limpio(e.mermaKg()) + " kg de merma y " + limpio(e.rechazoKg())
                            + " kg rechazados). Lo normal es hasta " + limpio(mermaAltaPorcentaje) + " %."));
        }
        return avisos;
    }

    private List<Aviso> deAlerta(AlertaDeEnvio a) {
        TipoAviso tipo = switch (a.tipo()) {
            case DETENIDO -> TipoAviso.ENVIO_DETENIDO;
            case GPS_DESACTIVADO -> TipoAviso.GPS_DESACTIVADO;
            case LLEGADA -> TipoAviso.ENVIO_LLEGADO;
            case RETRASO -> TipoAviso.ENVIO_RETRASADO;
        };
        String titulo = switch (tipo) {
            case ENVIO_DETENIDO -> "Envío detenido";
            case ENVIO_RETRASADO -> "Envío con retraso";
            case GPS_DESACTIVADO -> "El conductor apagó el GPS";
            default -> "El envío llegó a su destino";
        };
        return List.of(new Aviso("alerta-envio:" + a.idAlerta(), tipo, titulo + " (envío " + a.idEnvio() + ")",
                titulo + ": envío " + a.idEnvio() + " hacia " + a.destino() + ". " + a.descripcion()));
    }

    private Aviso deResumen(ResumenSemanal r) {
        Indicadores i = r.resumen().actual();
        String cuerpo = r.resumen().explicacion() + "\n\nDel " + r.desde() + " al " + r.hasta() + ":\n"
                + "- Entregas: " + i.entregas() + "\n"
                + "- Ingreso real: " + soles(i.ingresoReal()) + "\n"
                + "- Flete: " + soles(i.fleteTotal()) + "\n"
                + "- Merma y rechazo: " + soles(i.perdidaMermaRechazo()) + "\n"
                + "- Ganancia real: " + soles(i.gananciaReal()) + "\n"
                + "- Por cobrar: " + soles(i.porCobrar());
        return new Aviso("resumen-semanal:" + r.desde(), TipoAviso.RESUMEN_SEMANAL,
                "Resumen de la semana: " + soles(i.gananciaReal()) + " de ganancia", cuerpo);
    }

    private static String soles(Dinero dinero) {
        return String.format(Locale.US, "S/ %,.2f", dinero.monto());
    }

    private static String limpio(BigDecimal numero) {
        return numero.stripTrailingZeros().toPlainString();
    }
}
