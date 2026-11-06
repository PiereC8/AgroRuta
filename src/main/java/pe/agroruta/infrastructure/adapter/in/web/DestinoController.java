package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
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
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.port.in.AdministrarDestinosUseCase;

@RestController
@RequestMapping("/api/destinos")
public class DestinoController {

    private final AdministrarDestinosUseCase destinos;

    public DestinoController(AdministrarDestinosUseCase destinos) {
        this.destinos = destinos;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DestinoRespuesta registrar(@Valid @RequestBody DestinoSolicitud solicitud) {
        return DestinoRespuesta.de(destinos.registrarDestino(
                solicitud.nombreDestino(), solicitud.ciudadDestino(), solicitud.esLocal()));
    }

    @GetMapping
    public List<DestinoRespuesta> listar() {
        return destinos.listarDestinos().stream().map(DestinoRespuesta::de).toList();
    }

    @PutMapping("/{idDestinoVenta}/precio")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fijarPrecio(@PathVariable Long idDestinoVenta, @Valid @RequestBody PrecioSolicitud solicitud) {
        destinos.fijarPrecioEsperado(idDestinoVenta, solicitud.productoLote(), new Dinero(solicitud.precioPorKg()));
    }

    @PutMapping("/{idDestinoVenta}/coordenadas")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fijarCoordenadas(@PathVariable Long idDestinoVenta, @Valid @RequestBody CoordenadasSolicitud solicitud) {
        destinos.fijarCoordenadas(idDestinoVenta, solicitud.latitud(), solicitud.longitud());
    }

    @PutMapping("/{idDestinoVenta}/tarifa")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void fijarTarifa(@PathVariable Long idDestinoVenta, @Valid @RequestBody TarifaSolicitud solicitud) {
        destinos.fijarTarifaFlete(idDestinoVenta, new Dinero(solicitud.costoFletePorKg()));
    }

    public record DestinoSolicitud(@NotBlank String nombreDestino, @NotBlank String ciudadDestino,
                                   @NotNull Boolean esLocal) {
    }

    public record PrecioSolicitud(@NotBlank String productoLote, @NotNull BigDecimal precioPorKg) {
    }

    public record TarifaSolicitud(@NotNull BigDecimal costoFletePorKg) {
    }

    public record CoordenadasSolicitud(@NotNull Double latitud, @NotNull Double longitud) {
    }

    public record DestinoRespuesta(Long idDestinoVenta, String nombreDestino, String ciudadDestino,
                                   boolean esLocal) {
        static DestinoRespuesta de(DestinoVenta destino) {
            return new DestinoRespuesta(destino.idDestinoVenta(), destino.nombreDestino(), destino.ciudadDestino(),
                    destino.esLocal());
        }
    }
}
