package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import pe.agroruta.domain.model.Aviso;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Correo;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.Liquidacion;
import pe.agroruta.domain.model.Notificacion;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.TipoAviso;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.in.EnviarEnlacePagoUseCase;
import pe.agroruta.domain.port.in.GenerarEnlacesEnvioUseCase;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;
import pe.agroruta.domain.port.out.NotificacionRepositoryPort;

/** El correo va a la bandeja de salida: se envía en el siguiente minuto y se reintenta si falla. */
public class EnviarEnlacePagoService implements EnviarEnlacePagoUseCase {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "PE"))
            .withZone(ZoneId.of("America/Lima"));

    private final GenerarEnlacesEnvioUseCase enlaces;
    private final EnvioRepositoryPort envios;
    private final LiquidacionRepositoryPort liquidaciones;
    private final NotificacionRepositoryPort bandeja;
    private final Clock reloj;

    public EnviarEnlacePagoService(GenerarEnlacesEnvioUseCase enlaces, EnvioRepositoryPort envios,
                                   LiquidacionRepositoryPort liquidaciones, NotificacionRepositoryPort bandeja,
                                   Clock reloj) {
        this.enlaces = enlaces;
        this.envios = envios;
        this.liquidaciones = liquidaciones;
        this.bandeja = bandeja;
        this.reloj = reloj;
    }

    @Override
    public Resultado enviar(Comando comando) {
        String correo = Correo.normalizar(comando.correoComprador());
        if (correo == null) {
            throw new DominioException("Escribe el correo del comprador");
        }
        Envio envio = envios.buscarPorId(comando.idEnvio())
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + comando.idEnvio()));
        Liquidacion liquidacion = liquidaciones.buscarPorLote(envio.idLote())
                .orElseThrow(() -> new ConflictoDominioException(
                        "El pedido todavía no fue entregado: no hay nada que cobrar"));
        if (!liquidacion.ingresoReal().esPositivo()) {
            throw new ConflictoDominioException("Esta entrega no tiene importe a cobrar (S/ 0.00)");
        }

        // Solo se renueva el enlace del comprador; el del conductor no se toca.
        var generados = enlaces.generar(comando.identificadorNegocio(), envio.idEnvio(), TipoEnlace.COMPRADOR);
        String url = comando.urlBase() + "/pago.html#t=" + generados.tokenComprador();
        String monto = String.format(Locale.US, "S/ %,.2f", liquidacion.ingresoReal().monto());
        String asunto = "Enlace para pagar tu pedido N.º " + envio.idPedido();
        String cuerpo = "Hola,\n\n"
                + "Te enviamos el enlace para pagar el pedido N.º " + envio.idPedido() + " por " + monto + ".\n\n"
                + "Puedes pagar con tarjeta, PayPal o PagoEfectivo aquí:\n" + url + "\n\n"
                + "El enlace vence el " + FECHA.format(generados.fechaExpiracion()) + ". "
                + "Al pagar recibirás tu comprobante electrónico.\n\n"
                + "Enviado desde AgroRuta.";

        Instant ahora = reloj.instant();
        Aviso aviso = new Aviso("enlace-pago:" + envio.idEnvio() + ":" + ahora.toEpochMilli(), TipoAviso.ENLACE_PAGO,
                asunto, cuerpo);
        bandeja.encolar(Notificacion.nueva(aviso, correo, ahora));
        return new Resultado(correo, url, generados.fechaExpiracion());
    }
}
