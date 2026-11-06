package pe.agroruta.infrastructure.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.agroruta.application.service.AltaNegocioService;
import pe.agroruta.domain.port.in.AltaNegocioUseCase;
import pe.agroruta.domain.port.out.BaseNegocioPort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;

@Configuration
public class BeanConfig {

    @Bean
    AltaNegocioUseCase altaNegocioUseCase(NegocioRepositoryPort negocios, BaseNegocioPort bases) {
        return new AltaNegocioService(negocios, bases);
    }
}
