package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.NegocioYaExisteException;
import pe.agroruta.domain.model.Plan;
import pe.agroruta.domain.port.in.AltaNegocioUseCase.Comando;
import pe.agroruta.domain.port.out.BaseNegocioPort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;

class AltaNegocioServiceTest {

    private final List<String> pasos = new ArrayList<>();
    private final List<String> registrados = new ArrayList<>();
    private AltaNegocioService servicio;

    @BeforeEach
    void preparar() {
        NegocioRepositoryPort repositorio = new NegocioRepositoryPort() {
            @Override
            public boolean existePorIdentificador(String identificadorNegocio) {
                return registrados.contains(identificadorNegocio);
            }

            @Override
            public Negocio guardar(Negocio negocio) {
                pasos.add("guardar:" + negocio.identificadorNegocio());
                registrados.add(negocio.identificadorNegocio());
                return negocio.conId(1L);
            }
        };
        BaseNegocioPort bases = identificadorNegocio -> pasos.add("base:" + identificadorNegocio);
        servicio = new AltaNegocioService(repositorio, bases);
    }

    @Test
    void crea_la_base_y_despues_registra_el_negocio() {
        Negocio negocio = servicio.darDeAlta(new Comando("chacra_demo", "Chacra Demo", Plan.CHACRA));

        assertThat(pasos).containsExactly("base:chacra_demo", "guardar:chacra_demo");
        assertThat(negocio.idNegocio()).isEqualTo(1L);
    }

    @Test
    void no_permite_dos_negocios_con_el_mismo_identificador() {
        servicio.darDeAlta(new Comando("chacra_demo", "Chacra Demo", Plan.CHACRA));
        pasos.clear();

        assertThatThrownBy(() -> servicio.darDeAlta(new Comando("chacra_demo", "Otra", Plan.EMPRESA)))
                .isInstanceOf(NegocioYaExisteException.class);
        assertThat(pasos).isEmpty();
    }

    @Test
    void con_datos_invalidos_no_toca_ninguna_base() {
        assertThatThrownBy(() -> servicio.darDeAlta(new Comando("x`; DROP", "Demo", Plan.CHACRA)))
                .isInstanceOf(DominioException.class);
        assertThat(pasos).isEmpty();
    }

    @Test
    void si_falla_la_base_no_registra_el_negocio() {
        BaseNegocioPort falla = identificadorNegocio -> {
            throw new IllegalStateException("MySQL no responde");
        };
        NegocioRepositoryPort repositorio = new NegocioRepositoryPort() {
            @Override
            public boolean existePorIdentificador(String identificadorNegocio) {
                return false;
            }

            @Override
            public Negocio guardar(Negocio negocio) {
                pasos.add("guardar");
                return negocio;
            }
        };

        assertThatThrownBy(() -> new AltaNegocioService(repositorio, falla)
                .darDeAlta(new Comando("demo", "Demo", Plan.CHACRA)))
                .isInstanceOf(IllegalStateException.class);
        assertThat(pasos).isEmpty();
    }
}

