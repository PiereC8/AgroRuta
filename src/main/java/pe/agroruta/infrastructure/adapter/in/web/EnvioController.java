package pe.agroruta.infrastructure.adapter.in.web;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonUnwrapped;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import pe.agroruta.domain.model.AlertaEnvio;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.port.in.AvisarDespachoUseCase;
import pe.agroruta.domain.port.in.ConsultarSeguimientoUseCase;
import pe.agroruta.domain.port.in.DespacharEnvioUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

// RFF-08: API /api/envios (despachar, salir en ruta, listar, cancelar).
@RestController
@RequestMapping("/api/envios")
public class EnvioController {

    private static final Logger REGISTRO = LoggerFactory.getLogger(EnvioController.class);

    private final DespacharEnvioUseCase envios;
    private final AvisarDespachoUseCase avisarDespacho;
    private final ConsultarSeguimientoUseCase seguimiento;
    /** Dirección de las páginas cuando no las sirve este backend (por ejemplo, Vercel). Vacía: la del pedido. */
    private final String urlPublica;

    public EnvioController(DespacharEnvioUseCase envios, AvisarDespachoUseCase avisarDespacho,
                           ConsultarSeguimientoUseCase seguimiento,
                           @Value("${agroruta.web.url-publica:}") String urlPublica) {
        this.envios = envios;
        this.avisarDespacho = avisarDespacho;
        this.seguimiento = seguimiento;
        this.urlPublica = urlPublica.endsWith("/") ? urlPublica.substring(0, urlPublica.length() - 1) : urlPublica;
    }

    /**
     * Despacha y, aparte, deja listo el enlace del conductor y avisa al comprador por correo. Si el aviso falla, el
     * envío igual queda programado y los enlaces se pueden generar con el botón «Enlaces».
     * <p>
     * Requerimiento: despachar (RF-09 / RFF-08) + enlaces automáticos (RF-14 y RF-21 / RFF-09).
     * Cómo: primero despacha; luego llama a AvisarDespachoUseCase dentro de un try para que un fallo no deshaga
     * el despacho.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DespachoRespuesta despachar(@Valid @RequestBody DespachoSolicitud solicitud,
                                       @AuthenticationPrincipal DatosToken usuario) {
        Envio envio = envios.despachar(solicitud.idPedido(), solicitud.idVehiculo(), usuario.nombreUsuario());
        AvisarDespachoUseCase.Resultado aviso = null;
        try {
            aviso = avisarDespacho.avisar(usuario.identificadorNegocio(), envio.idEnvio(), urlPublica.isBlank()
                    ? ServletUriComponentsBuilder.fromCurrentContextPath().toUriString() : urlPublica);
        } catch (RuntimeException e) {
            REGISTRO.warn("Envío {} programado, pero no se pudieron preparar sus enlaces: {}", envio.idEnvio(),
                    e.getMessage());
        }
        return DespachoRespuesta.de(envio, aviso);
    }

    @PostMapping("/{idEnvio}/salida")
    public EnvioRespuesta iniciarRuta(@PathVariable Long idEnvio, @AuthenticationPrincipal DatosToken usuario) {
        return EnvioRespuesta.de(envios.iniciarRuta(idEnvio, usuario.nombreUsuario()));
    }

    @GetMapping
    public List<EnvioRespuesta> listar(@RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "50") int tamano) {
        List<Envio> lista = envios.listar(pagina, tamano);
        Map<Long, AlertaEnvio> sinSenal = seguimiento.sinSenal(lista);
        return lista.stream().map(e -> EnvioRespuesta.de(e, sinSenal.get(e.idEnvio()))).toList();
    }

    @GetMapping("/{idEnvio}")
    public EnvioRespuesta obtener(@PathVariable Long idEnvio) {
        return EnvioRespuesta.de(envios.obtener(idEnvio));
    }

    public record DespachoSolicitud(@NotNull Long idPedido, @NotNull Long idVehiculo) {
    }

    /** Los datos del envío más lo necesario para mandarle su enlace al conductor por WhatsApp. */
    public record DespachoRespuesta(@JsonUnwrapped EnvioRespuesta envio, String urlConductor,
                                    String nombreTransportista, String telefonoTransportista,
                                    String correoComprador, Instant fechaExpiracion) {
        static DespachoRespuesta de(Envio envio, AvisarDespachoUseCase.Resultado aviso) {
            return aviso == null ? new DespachoRespuesta(EnvioRespuesta.de(envio), null, null, null, null, null)
                    : new DespachoRespuesta(EnvioRespuesta.de(envio), aviso.urlConductor(),
                    aviso.nombreTransportista(), aviso.telefonoTransportista(), aviso.correoComprador(),
                    aviso.fechaExpiracion());
        }
    }

    public record EnvioRespuesta(Long idEnvio, Long idPedido, Long idLote, Long idVehiculo, Long idTransportista,
                                 EstadoEnvio estadoEnvio, Instant fechaProgramada, Instant fechaSalida,
                                 Instant fechaEntrega, String usuarioDespacho, String sinSenal) {
        static EnvioRespuesta de(Envio envio) {
            return de(envio, null);
        }

        /** sinSenal: texto de la alerta «Sin señal del conductor hace X», o null. */
        static EnvioRespuesta de(Envio envio, AlertaEnvio sinSenal) {
            return new EnvioRespuesta(envio.idEnvio(), envio.idPedido(), envio.idLote(), envio.idVehiculo(),
                    envio.idTransportista(), envio.estadoEnvio(), envio.fechaProgramada(), envio.fechaSalida(),
                    envio.fechaEntrega(), envio.usuarioDespacho(),
                    sinSenal == null ? null : sinSenal.descripcionAlerta());
        }
    }
}
