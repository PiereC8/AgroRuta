package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

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
import pe.agroruta.domain.model.CondicionesDestino;
import pe.agroruta.domain.port.in.AdministrarDestinosUseCase;
import pe.agroruta.domain.port.in.ConsultarCondicionesDestinosUseCase;

// RFF-04: API /api/destinos (registrar, precio, tarifa y coordenadas).
@RestController
@RequestMapping("/api/destinos")
public class DestinoController {

    private final AdministrarDestinosUseCase destinos;
    private final ConsultarCondicionesDestinosUseCase condiciones;

    public DestinoController(AdministrarDestinosUseCase destinos, ConsultarCondicionesDestinosUseCase condiciones) {
        this.destinos = destinos;
        this.condiciones = condiciones;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public DestinoRespuesta registrar(@Valid @RequestBody DestinoSolicitud solicitud) {
        return DestinoRespuesta.de(destinos.registrarDestino(
                solicitud.nombreDestino(), solicitud.ciudadDestino(), solicitud.esLocal()), CondicionesDestino.VACIAS);
    }

    @GetMapping
    public List<DestinoRespuesta> listar() {
        Map<Long, CondicionesDestino> vigentes = condiciones.vigentes();
        return destinos.listarDestinos().stream()
                .map(d -> DestinoRespuesta.de(d, vigentes.getOrDefault(d.idDestinoVenta(), CondicionesDestino.VACIAS)))
                .toList();
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

    /** Latitud y longitud en null si el destino todavía no tiene ubicación. */
    @GetMapping("/{idDestinoVenta}/coordenadas")
    public CoordenadasRespuesta coordenadas(@PathVariable Long idDestinoVenta) {
        return destinos.coordenadasDe(idDestinoVenta)
                .map(c -> new CoordenadasRespuesta(c.latitud(), c.longitud()))
                .orElse(new CoordenadasRespuesta(null, null));
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

    public record CoordenadasRespuesta(Double latitud, Double longitud) {
    }

    public record CoordenadasSolicitud(@NotNull Double latitud, @NotNull Double longitud) {
    }

    /** costoFletePorKg: null si no tiene flete fijado. precios: precio esperado por kg de cada producto. */
    public record DestinoRespuesta(Long idDestinoVenta, String nombreDestino, String ciudadDestino,
                                   boolean esLocal, BigDecimal costoFletePorKg, List<PrecioRespuesta> precios) {
        static DestinoRespuesta de(DestinoVenta destino, CondicionesDestino condiciones) {
            return new DestinoRespuesta(destino.idDestinoVenta(), destino.nombreDestino(), destino.ciudadDestino(),
                    destino.esLocal(),
                    condiciones.costoFletePorKg() == null ? null : condiciones.costoFletePorKg().monto(),
                    condiciones.preciosPorKg().entrySet().stream()
                            .map(e -> new PrecioRespuesta(e.getKey(), e.getValue().monto())).toList());
        }
    }

    public record PrecioRespuesta(String productoLote, BigDecimal precioPorKg) {
    }
}
