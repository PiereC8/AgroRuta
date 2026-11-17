package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

import pe.agroruta.domain.model.Aviso;
import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.Notificacion;
import pe.agroruta.domain.model.Pedido;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.TipoAviso;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.model.Transportista;
import pe.agroruta.domain.port.in.AvisarDespachoUseCase;
import pe.agroruta.domain.port.in.GenerarEnlacesEnvioUseCase;
import pe.agroruta.domain.port.out.CompradorRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.NotificacionRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.TransporteRepositoryPort;

/**
 * RF-14 y RF-21 (finales: RFF-09 enlaces y RFF-22 avisos por correo): al despachar, arma los enlaces de seguimiento para que el operador no los copie a mano.
 * Cómo: busca envío → pedido → comprador y transportista; genera los tokens; si el comprador tiene correo,
 * deja el correo en la bandeja de salida (se envía en el siguiente minuto y se reintenta si falla).
 */
public class AvisarDespachoService implements AvisarDespachoUseCase {

    private static final DateTimeFormatter FECHA = DateTimeFormatter.ofPattern("dd/MM/yyyy", new Locale("es", "PE"))
            .withZone(ZoneId.of("America/Lima"));

    private final GenerarEnlacesEnvioUseCase enlaces;
    private final EnvioRepositoryPort envios;
    private final PedidoRepositoryPort pedidos;
    private final CompradorRepositoryPort compradores;
    private final TransporteRepositoryPort transporte;
    private final NotificacionRepositoryPort bandeja;
    private final Clock reloj;

    public AvisarDespachoService(GenerarEnlacesEnvioUseCase enlaces, EnvioRepositoryPort envios,
                                 PedidoRepositoryPort pedidos, CompradorRepositoryPort compradores,
                                 TransporteRepositoryPort transporte, NotificacionRepositoryPort bandeja,
                                 Clock reloj) {
        this.enlaces = enlaces;
        this.envios = envios;
        this.pedidos = pedidos;
        this.compradores = compradores;
        this.transporte = transporte;
        this.bandeja = bandeja;
        this.reloj = reloj;
    }

    @Override
    public Resultado avisar(String identificadorNegocio, Long idEnvio, String urlBase) {
        // 1. Datos necesarios: el envío, su comprador (por el pedido) y el transportista.
        Envio envio = envios.buscarPorId(idEnvio)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe el envío " + idEnvio));
        Comprador comprador = pedidos.buscarPorId(envio.idPedido()).map(Pedido::idComprador)
                .flatMap(compradores::buscarPorId).orElse(null);
        String correo = comprador == null ? null : comprador.correoComprador();
        Transportista transportista = transporte.buscarTransportista(envio.idTransportista()).orElse(null);

        // 2. RFF-09: genera los enlaces (tokens con vencimiento).
        // Sin correo del comprador no se genera su enlace: lo puede pedir después con el botón «Enlaces».
        var generados = enlaces.generar(identificadorNegocio, idEnvio, correo == null ? TipoEnlace.CONDUCTOR : null);
        // 3. RF-14 / RFF-22: correo al comprador con su enlace de seguimiento (va a la bandeja, no se envía aquí mismo).
        if (correo != null) {
            String url = urlBase + "/seguimiento.html#t=" + generados.tokenComprador();
            String cuerpo = "Hola, " + comprador.nombreComprador() + ":\n\n"
                    + "Tu pedido N.º " + envio.idPedido() + " ya fue despachado. Puedes ver por dónde va y a qué hora "
                    + "llega aquí:\n" + url + "\n\n"
                    + "El enlace vence el " + FECHA.format(generados.fechaExpiracion()) + ". "
                    + "No lo compartas: quien lo tenga puede ver tu pedido.\n\n"
                    + "Enviado desde AgroRuta.";
            Instant ahora = reloj.instant();
            bandeja.encolar(Notificacion.nueva(new Aviso("seguimiento:" + idEnvio + ":" + ahora.toEpochMilli(),
                    TipoAviso.SEGUIMIENTO_ENVIO, "Tu pedido N.º " + envio.idPedido() + " está en camino", cuerpo),
                    correo, ahora));
        }
        // 4. RF-21 / RFF-09: devuelve el enlace del conductor y su teléfono; la pantalla lo manda por WhatsApp.
        return new Resultado(urlBase + "/conductor.html#t=" + generados.tokenConductor(),
                transportista == null ? null : transportista.nombreTransportista(),
                transportista == null ? null : transportista.telefonoTransportista(), correo,
                generados.fechaExpiracion());
    }
}
