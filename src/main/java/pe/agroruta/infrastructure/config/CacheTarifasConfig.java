package pe.agroruta.infrastructure.config;

import java.time.Clock;
import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import pe.agroruta.domain.port.out.PreciosDestinoPort;
import pe.agroruta.domain.port.out.TarifaPort;
import pe.agroruta.infrastructure.adapter.out.cache.MemoriaConExpiracion;
import pe.agroruta.infrastructure.adapter.out.cache.PreciosDestinoPortConCache;
import pe.agroruta.infrastructure.adapter.out.cache.TarifaPortConCache;
import pe.agroruta.infrastructure.adapter.out.persistence.DestinoJdbcAdapter;
import pe.agroruta.infrastructure.adapter.out.persistence.TarifaManualJdbcAdapter;

/** Pone la caché delante de las tarifas y los precios. Los servicios reciben estos dos en lugar de los de la base. */
@Configuration
public class CacheTarifasConfig {

    @Bean
    MemoriaConExpiracion memoriaDeTarifas(Clock reloj,
                                          @Value("${agroruta.cache.tarifas-segundos:300}") int segundos) {
        return new MemoriaConExpiracion(Duration.ofSeconds(segundos), reloj);
    }

    @Bean
    @Primary
    TarifaPort tarifaPortConCache(TarifaManualJdbcAdapter tarifas, MemoriaConExpiracion memoriaDeTarifas) {
        return new TarifaPortConCache(tarifas, memoriaDeTarifas);
    }

    @Bean
    @Primary
    PreciosDestinoPort preciosDestinoPortConCache(DestinoJdbcAdapter precios, MemoriaConExpiracion memoriaDeTarifas) {
        return new PreciosDestinoPortConCache(precios, memoriaDeTarifas);
    }
}
