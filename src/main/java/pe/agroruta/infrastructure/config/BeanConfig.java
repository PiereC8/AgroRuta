package pe.agroruta.infrastructure.config;

import java.time.Clock;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.agroruta.application.service.AltaNegocioService;
import pe.agroruta.application.service.ConsultarLotesService;
import pe.agroruta.application.service.IniciarSesionService;
import pe.agroruta.application.service.RegistrarLoteService;
import pe.agroruta.domain.port.in.AltaNegocioUseCase;
import pe.agroruta.domain.port.in.ConsultarLotesUseCase;
import pe.agroruta.domain.port.in.IniciarSesionUseCase;
import pe.agroruta.domain.port.in.RegistrarLoteUseCase;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.BaseNegocioPort;
import pe.agroruta.domain.port.out.EmisorTokenPort;
import pe.agroruta.domain.port.out.HashClavePort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;

@Configuration
public class BeanConfig {

    @Bean
    Clock reloj() {
        return Clock.system(ZoneId.of("America/Lima"));
    }

    @Bean
    AltaNegocioUseCase altaNegocioUseCase(NegocioRepositoryPort negocios, BaseNegocioPort bases,
                                          UsuarioRepositoryPort usuarios, HashClavePort hashes) {
        return new AltaNegocioService(negocios, bases, usuarios, hashes);
    }

    @Bean
    RegistrarLoteUseCase registrarLoteUseCase(LoteRepositoryPort lotes, Clock reloj) {
        return new RegistrarLoteService(lotes, reloj);
    }

    @Bean
    ConsultarLotesUseCase consultarLotesUseCase(LoteRepositoryPort lotes) {
        return new ConsultarLotesService(lotes);
    }

    @Bean
    IniciarSesionUseCase iniciarSesionUseCase(UsuarioRepositoryPort usuarios, NegocioRepositoryPort negocios,
                                              HashClavePort hashes, EmisorTokenPort emisor, Clock reloj) {
        return new IniciarSesionService(usuarios, negocios, hashes, emisor, reloj);
    }
}
