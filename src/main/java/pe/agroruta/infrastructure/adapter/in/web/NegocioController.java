package pe.agroruta.infrastructure.adapter.in.web;

import org.springframework.http.HttpStatus;
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
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.Plan;
import pe.agroruta.domain.model.DatosFiscales;
import pe.agroruta.domain.port.in.AltaNegocioUseCase;
import pe.agroruta.domain.port.in.RegistrarDatosFiscalesUseCase;

@RestController
@RequestMapping("/api/plataforma/negocios")
public class NegocioController {

    private final AltaNegocioUseCase altaNegocio;
    private final RegistrarDatosFiscalesUseCase datosFiscales;

    public NegocioController(AltaNegocioUseCase altaNegocio, RegistrarDatosFiscalesUseCase datosFiscales) {
        this.altaNegocio = altaNegocio;
        this.datosFiscales = datosFiscales;
    }

    /** RUC, razón social y domicilio fiscal con los que el negocio emite sus boletas y facturas. */
    @PutMapping("/{identificadorNegocio}/datos-fiscales")
    public DatosFiscales registrarDatosFiscales(@PathVariable String identificadorNegocio,
                                                @Valid @RequestBody DatosFiscalesSolicitud solicitud) {
        return datosFiscales.registrar(identificadorNegocio, solicitud.ruc(), solicitud.razonSocial(),
                solicitud.domicilioFiscal());
    }

    public record DatosFiscalesSolicitud(@NotBlank String ruc, @NotBlank String razonSocial,
                                         @NotBlank String domicilioFiscal) {
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public NegocioRespuesta crear(@Valid @RequestBody NegocioSolicitud solicitud) {
        Negocio negocio = altaNegocio.darDeAlta(new AltaNegocioUseCase.Comando(
                solicitud.identificadorNegocio(), solicitud.nombreNegocio(), solicitud.planSuscripcion(),
                solicitud.nombreUsuarioDueno(), solicitud.claveInicialDueno()));
        return new NegocioRespuesta(negocio.idNegocio(), negocio.identificadorNegocio(),
                negocio.nombreNegocio(), negocio.planSuscripcion());
    }

    public record NegocioSolicitud(@NotBlank String identificadorNegocio, @NotBlank String nombreNegocio,
                                   @NotNull Plan planSuscripcion, @NotBlank String nombreUsuarioDueno,
                                   @NotBlank String claveInicialDueno) {
    }

    public record NegocioRespuesta(Long idNegocio, String identificadorNegocio, String nombreNegocio,
                                   Plan planSuscripcion) {
    }
}
