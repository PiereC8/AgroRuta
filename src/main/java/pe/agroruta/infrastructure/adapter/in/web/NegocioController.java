package pe.agroruta.infrastructure.adapter.in.web;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Plan;
import pe.agroruta.domain.port.in.AltaNegocioUseCase;

@RestController
@RequestMapping("/api/plataforma/negocios")
public class NegocioController {

    private final AltaNegocioUseCase altaNegocio;

    public NegocioController(AltaNegocioUseCase altaNegocio) {
        this.altaNegocio = altaNegocio;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NegocioRespuesta crear(@Valid @RequestBody NegocioSolicitud solicitud) {
        Negocio negocio = altaNegocio.darDeAlta(new AltaNegocioUseCase.Comando(
                solicitud.identificadorNegocio(), solicitud.nombreNegocio(), solicitud.planSuscripcion()));
        return new NegocioRespuesta(negocio.idNegocio(), negocio.identificadorNegocio(),
                negocio.nombreNegocio(), negocio.planSuscripcion());
    }

    public record NegocioSolicitud(@NotBlank String identificadorNegocio, @NotBlank String nombreNegocio,
                                   @NotNull Plan planSuscripcion) {
    }

    public record NegocioRespuesta(Long idNegocio, String identificadorNegocio, String nombreNegocio,
                                   Plan planSuscripcion) {
    }
}
