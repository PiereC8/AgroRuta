package pe.agroruta.infrastructure.adapter.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import pe.agroruta.domain.model.MiembroEquipo;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.AdministrarEquipoUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

/** Las personas del negocio y sus correos para los avisos. Solo el Dueño llega aquí. */
@RestController
@RequestMapping("/api/equipo")
public class EquipoController {

    private final AdministrarEquipoUseCase equipo;

    public EquipoController(AdministrarEquipoUseCase equipo) {
        this.equipo = equipo;
    }

    @GetMapping
    public List<MiembroRespuesta> listar() {
        return equipo.listar().stream().map(MiembroRespuesta::de).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public MiembroRespuesta crearOperador(@Valid @RequestBody OperadorSolicitud solicitud) {
        return MiembroRespuesta.de(equipo.crearOperador(solicitud.nombreUsuario(), solicitud.clave(),
                solicitud.correo()));
    }

    @PutMapping("/{idUsuario}/correo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarCorreo(@PathVariable Long idUsuario, @Valid @RequestBody CorreoSolicitud solicitud) {
        equipo.cambiarCorreo(idUsuario, solicitud.correo(), solicitud.avisosActivos());
    }

    @PutMapping("/{idUsuario}/activo")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void cambiarActivo(@AuthenticationPrincipal DatosToken usuario, @PathVariable Long idUsuario,
                              @Valid @RequestBody ActivoSolicitud solicitud) {
        equipo.cambiarActivo(idUsuario, solicitud.activo(), usuario.nombreUsuario());
    }

    @PutMapping("/{idUsuario}/clave")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void restablecerClave(@AuthenticationPrincipal DatosToken usuario, @PathVariable Long idUsuario,
                                 @Valid @RequestBody ClaveSolicitud solicitud) {
        equipo.restablecerClave(idUsuario, solicitud.claveNueva(), usuario.nombreUsuario());
    }

    public record ClaveSolicitud(@NotBlank @Size(max = 72) String claveNueva) {
    }

    public record OperadorSolicitud(@NotBlank @Size(max = 50) String nombreUsuario,
                                    @NotBlank @Size(max = 72) String clave, @Size(max = 120) String correo) {
    }

    public record CorreoSolicitud(@Size(max = 120) String correo, @NotNull Boolean avisosActivos) {
    }

    public record ActivoSolicitud(@NotNull Boolean activo) {
    }

    public record MiembroRespuesta(Long idUsuario, String nombreUsuario, Rol rolUsuario, String correo,
                                   boolean avisosActivos, boolean usuarioActivo) {
        static MiembroRespuesta de(MiembroEquipo m) {
            return new MiembroRespuesta(m.idUsuario(), m.nombreUsuario(), m.rol(), m.correo(), m.avisosActivos(),
                    m.activo());
        }
    }
}
