package pe.agroruta.infrastructure.adapter.in.web;

import java.time.Instant;
import java.util.List;

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

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import pe.agroruta.domain.model.Envio;
import pe.agroruta.domain.model.EstadoEnvio;
import pe.agroruta.domain.port.in.DespacharEnvioUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

@RestController
@RequestMapping("/api/envios")
public class EnvioController {

    private final DespacharEnvioUseCase envios;

    public EnvioController(DespacharEnvioUseCase envios) {
        this.envios = envios;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public EnvioRespuesta despachar(@Valid @RequestBody DespachoSolicitud solicitud,
                                    @AuthenticationPrincipal DatosToken usuario) {
        return EnvioRespuesta.de(envios.despachar(solicitud.idPedido(), solicitud.idVehiculo(),
                usuario.nombreUsuario()));
    }

    @PostMapping("/{idEnvio}/salida")
    public EnvioRespuesta iniciarRuta(@PathVariable Long idEnvio, @AuthenticationPrincipal DatosToken usuario) {
        return EnvioRespuesta.de(envios.iniciarRuta(idEnvio, usuario.nombreUsuario()));
    }

    @GetMapping
    public List<EnvioRespuesta> listar(@RequestParam(defaultValue = "0") int pagina,
                                       @RequestParam(defaultValue = "50") int tamano) {
        return envios.listar(pagina, tamano).stream().map(EnvioRespuesta::de).toList();
    }

    @GetMapping("/{idEnvio}")
    public EnvioRespuesta obtener(@PathVariable Long idEnvio) {
        return EnvioRespuesta.de(envios.obtener(idEnvio));
    }

    public record DespachoSolicitud(@NotNull Long idPedido, @NotNull Long idVehiculo) {
    }

    public record EnvioRespuesta(Long idEnvio, Long idPedido, Long idLote, Long idVehiculo, Long idTransportista,
                                 EstadoEnvio estadoEnvio, Instant fechaProgramada, Instant fechaSalida,
                                 Instant fechaEntrega, String usuarioDespacho) {
        static EnvioRespuesta de(Envio envio) {
            return new EnvioRespuesta(envio.idEnvio(), envio.idPedido(), envio.idLote(), envio.idVehiculo(),
                    envio.idTransportista(), envio.estadoEnvio(), envio.fechaProgramada(), envio.fechaSalida(),
                    envio.fechaEntrega(), envio.usuarioDespacho());
        }
    }
}
