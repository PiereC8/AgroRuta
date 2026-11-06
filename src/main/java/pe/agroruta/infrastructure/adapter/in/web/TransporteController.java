package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pe.agroruta.domain.model.Transportista;
import pe.agroruta.domain.model.Vehiculo;
import pe.agroruta.domain.port.in.AdministrarTransporteUseCase;

@RestController
public class TransporteController {

    private final AdministrarTransporteUseCase transporte;

    public TransporteController(AdministrarTransporteUseCase transporte) {
        this.transporte = transporte;
    }

    @PostMapping("/api/transportistas")
    @ResponseStatus(HttpStatus.CREATED)
    public TransportistaRespuesta registrarTransportista(@Valid @RequestBody TransportistaSolicitud solicitud) {
        return TransportistaRespuesta.de(transporte.registrarTransportista(
                solicitud.nombreTransportista(), solicitud.telefonoTransportista()));
    }

    @GetMapping("/api/transportistas")
    public List<TransportistaRespuesta> listarTransportistas() {
        return transporte.listarTransportistas().stream().map(TransportistaRespuesta::de).toList();
    }

    @PostMapping("/api/vehiculos")
    @ResponseStatus(HttpStatus.CREATED)
    public VehiculoRespuesta registrarVehiculo(@Valid @RequestBody VehiculoSolicitud solicitud) {
        return VehiculoRespuesta.de(transporte.registrarVehiculo(
                solicitud.idTransportista(), solicitud.placaVehiculo(), solicitud.capacidadKg()));
    }

    @GetMapping("/api/vehiculos")
    public List<VehiculoRespuesta> listarVehiculos() {
        return transporte.listarVehiculos().stream().map(VehiculoRespuesta::de).toList();
    }

    public record TransportistaSolicitud(@NotBlank String nombreTransportista, String telefonoTransportista) {
    }

    public record TransportistaRespuesta(Long idTransportista, String nombreTransportista,
                                         String telefonoTransportista) {
        static TransportistaRespuesta de(Transportista transportista) {
            return new TransportistaRespuesta(transportista.idTransportista(), transportista.nombreTransportista(),
                    transportista.telefonoTransportista());
        }
    }

    public record VehiculoSolicitud(@NotNull Long idTransportista, @NotBlank String placaVehiculo,
                                    @NotNull BigDecimal capacidadKg) {
    }

    public record VehiculoRespuesta(Long idVehiculo, Long idTransportista, String placaVehiculo,
                                    BigDecimal capacidadKg) {
        static VehiculoRespuesta de(Vehiculo vehiculo) {
            return new VehiculoRespuesta(vehiculo.idVehiculo(), vehiculo.idTransportista(),
                    vehiculo.placaVehiculo(), vehiculo.capacidadKg());
        }
    }
}
