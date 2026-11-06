package pe.agroruta.infrastructure.adapter.in.web;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
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
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import pe.agroruta.domain.model.EstadoLote;
import pe.agroruta.domain.model.EventoLote;
import pe.agroruta.domain.model.Lote;
import pe.agroruta.domain.model.TipoEventoLote;
import pe.agroruta.domain.port.in.ConsultarLotesUseCase;
import pe.agroruta.domain.port.in.RegistrarLoteUseCase;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt.DatosToken;

@RestController
@RequestMapping("/api/lotes")
public class LoteController {

    private final RegistrarLoteUseCase registrarLote;
    private final ConsultarLotesUseCase consultarLotes;

    public LoteController(RegistrarLoteUseCase registrarLote, ConsultarLotesUseCase consultarLotes) {
        this.registrarLote = registrarLote;
        this.consultarLotes = consultarLotes;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public LoteRespuesta registrar(@Valid @RequestBody LoteSolicitud solicitud,
                                   @AuthenticationPrincipal DatosToken usuario) {
        return LoteRespuesta.de(registrarLote.registrar(new RegistrarLoteUseCase.Comando(
                solicitud.productoLote(), solicitud.cantidadKg(), solicitud.fechaCosecha(),
                solicitud.chacraOrigen(), usuario.nombreUsuario())));
    }

    @GetMapping
    public List<LoteRespuesta> listar(@RequestParam(defaultValue = "0") int pagina,
                                      @RequestParam(defaultValue = "50") int tamano) {
        return consultarLotes.listar(pagina, tamano).stream().map(LoteRespuesta::de).toList();
    }

    @GetMapping("/{idLote}")
    public LoteRespuesta obtener(@PathVariable Long idLote) {
        return LoteRespuesta.de(consultarLotes.obtener(idLote));
    }

    @GetMapping("/{idLote}/historial")
    public List<EventoRespuesta> historial(@PathVariable Long idLote) {
        return consultarLotes.historial(idLote).stream().map(EventoRespuesta::de).toList();
    }

    public record LoteSolicitud(@NotBlank String productoLote, @NotNull BigDecimal cantidadKg,
                                @NotNull LocalDate fechaCosecha, @NotBlank String chacraOrigen) {
    }

    public record LoteRespuesta(Long idLote, String productoLote, BigDecimal cantidadKg, LocalDate fechaCosecha,
                                String chacraOrigen, EstadoLote estadoLote, String usuarioRegistro,
                                Instant fechaRegistro) {
        static LoteRespuesta de(Lote lote) {
            return new LoteRespuesta(lote.idLote(), lote.productoLote(), lote.cantidadKg(), lote.fechaCosecha(),
                    lote.chacraOrigen(), lote.estadoLote(), lote.usuarioRegistro(), lote.fechaRegistro());
        }
    }

    public record EventoRespuesta(TipoEventoLote tipoEvento, String descripcionEvento, String usuarioEvento,
                                  Instant fechaEvento) {
        static EventoRespuesta de(EventoLote evento) {
            return new EventoRespuesta(evento.tipoEvento(), evento.descripcionEvento(), evento.usuarioEvento(),
                    evento.fechaEvento());
        }
    }
}
