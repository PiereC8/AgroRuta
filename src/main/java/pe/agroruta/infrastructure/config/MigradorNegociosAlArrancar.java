package pe.agroruta.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.port.out.BaseNegocioPort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;

/** Al arrancar, lleva la base de cada negocio activo a la última versión del esquema. */
@Component
public class MigradorNegociosAlArrancar implements ApplicationRunner {

    private static final Logger REGISTRO = LoggerFactory.getLogger(MigradorNegociosAlArrancar.class);

    private final NegocioRepositoryPort negocios;
    private final BaseNegocioPort bases;

    public MigradorNegociosAlArrancar(NegocioRepositoryPort negocios, BaseNegocioPort bases) {
        this.negocios = negocios;
        this.bases = bases;
    }

    @Override
    public void run(ApplicationArguments args) {
        for (Negocio negocio : negocios.listarActivos()) {
            try {
                bases.crearYMigrar(negocio.identificadorNegocio());
            } catch (RuntimeException e) {
                REGISTRO.error("No se pudo migrar la base del negocio {}: {}", negocio.identificadorNegocio(),
                        e.getMessage());
            }
        }
    }
}
