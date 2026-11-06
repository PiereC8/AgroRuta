package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.Falsos.HashFalso;
import pe.agroruta.application.service.Falsos.NegociosEnMemoria;
import pe.agroruta.application.service.Falsos.UsuariosEnMemoria;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.NegocioYaExisteException;
import pe.agroruta.domain.model.Plan;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.in.AltaNegocioUseCase.Comando;
import pe.agroruta.domain.port.out.BaseNegocioPort;

class AltaNegocioServiceTest {

    private final List<String> pasos = new ArrayList<>();
    private NegociosEnMemoria negocios;
    private UsuariosEnMemoria usuarios;
    private AltaNegocioService servicio;

    @BeforeEach
    void preparar() {
        negocios = new NegociosEnMemoria(pasos);
        usuarios = new UsuariosEnMemoria(pasos);
        BaseNegocioPort bases = identificadorNegocio -> pasos.add("base:" + identificadorNegocio);
        servicio = new AltaNegocioService(negocios, bases, usuarios, new HashFalso());
    }

    private static Comando comando(String identificador) {
        return new Comando(identificador, "Chacra Demo", Plan.CHACRA, "dueno.demo", "clave-segura-1");
    }

    @Test
    void crea_la_base_el_dueno_y_despues_registra_el_negocio() {
        Negocio negocio = servicio.darDeAlta(comando("chacra_demo"));

        assertThat(pasos).containsExactly("base:chacra_demo", "usuario:dueno.demo", "guardar:chacra_demo");
        assertThat(negocio.idNegocio()).isEqualTo(1L);
        var dueno = usuarios.buscarPorNombre("chacra_demo", "dueno.demo").orElseThrow();
        assertThat(dueno.rolUsuario()).isEqualTo(Rol.DUENO);
        assertThat(dueno.hashClave()).isEqualTo("hash:clave-segura-1");
    }

    @Test
    void no_permite_dos_negocios_con_el_mismo_identificador() {
        servicio.darDeAlta(comando("chacra_demo"));
        pasos.clear();

        assertThatThrownBy(() -> servicio.darDeAlta(comando("chacra_demo")))
                .isInstanceOf(NegocioYaExisteException.class);
        assertThat(pasos).isEmpty();
    }

    @Test
    void con_datos_invalidos_no_toca_ninguna_base() {
        assertThatThrownBy(() -> servicio.darDeAlta(comando("x`; DROP")))
                .isInstanceOf(DominioException.class);
        assertThat(pasos).isEmpty();
    }

    @Test
    void rechaza_una_clave_corta_antes_de_crear_la_base() {
        var corta = new Comando("chacra_demo", "Chacra Demo", Plan.CHACRA, "dueno.demo", "corta");

        assertThatThrownBy(() -> servicio.darDeAlta(corta)).isInstanceOf(DominioException.class);
        assertThat(pasos).isEmpty();
    }

    @Test
    void si_falla_la_base_no_registra_el_negocio() {
        BaseNegocioPort falla = identificadorNegocio -> {
            throw new IllegalStateException("MySQL no responde");
        };

        assertThatThrownBy(() -> new AltaNegocioService(negocios, falla, usuarios, new HashFalso())
                .darDeAlta(comando("chacra_demo")))
                .isInstanceOf(IllegalStateException.class);
        assertThat(pasos).isEmpty();
    }

    @Test
    void al_repetir_un_alta_interrumpida_no_duplica_al_dueno() {
        usuarios.guardar("chacra_demo", pe.agroruta.domain.model.Usuario.nuevo("dueno.demo", "hash:x", Rol.DUENO));
        pasos.clear();

        servicio.darDeAlta(comando("chacra_demo"));

        assertThat(pasos).containsExactly("base:chacra_demo", "guardar:chacra_demo");
    }
}
