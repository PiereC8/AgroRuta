package pe.agroruta.infrastructure.adapter.in.web;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.TipoComprador;
import pe.agroruta.domain.port.in.AdministrarCompradoresUseCase;

@RestController
@RequestMapping("/api/compradores")
public class CompradorController {

    private final AdministrarCompradoresUseCase compradores;

    public CompradorController(AdministrarCompradoresUseCase compradores) {
        this.compradores = compradores;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CompradorRespuesta registrar(@Valid @RequestBody CompradorSolicitud solicitud) {
        return CompradorRespuesta.de(compradores.registrar(solicitud.nombreComprador(), solicitud.tipoComprador(),
                solicitud.documentoComprador(), solicitud.telefonoComprador(), solicitud.ciudadComprador()));
    }

    @GetMapping
    public List<CompradorRespuesta> listar() {
        return compradores.listar().stream().map(CompradorRespuesta::de).toList();
    }

    public record CompradorSolicitud(@NotBlank String nombreComprador, @NotNull TipoComprador tipoComprador,
                                     String documentoComprador, String telefonoComprador,
                                     @NotBlank String ciudadComprador) {
    }

    public record CompradorRespuesta(Long idComprador, String nombreComprador, TipoComprador tipoComprador,
                                     String documentoComprador, String telefonoComprador, String ciudadComprador) {
        static CompradorRespuesta de(Comprador comprador) {
            return new CompradorRespuesta(comprador.idComprador(), comprador.nombreComprador(),
                    comprador.tipoComprador(), comprador.documentoComprador(), comprador.telefonoComprador(),
                    comprador.ciudadComprador());
        }
    }
}
