package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.Falsos.HashFalso;
import pe.agroruta.application.service.Falsos.UsuariosEnMemoria;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.Usuario;

class CambiarClaveServiceTest {

    private final UsuariosEnMemoria usuarios = new UsuariosEnMemoria();
    private final HashFalso hashes = new HashFalso();
    private CambiarClaveService servicio;

    @BeforeEach
    void preparar() {
        usuarios.guardar("chacra_calana", Usuario.nuevo("rosa.quispe", "hash:ClaveVieja-1", Rol.DUENO));
        servicio = new CambiarClaveService(usuarios, hashes);
    }

    @Test
    void con_la_clave_actual_correcta_se_cambia() {
        servicio.cambiar("chacra_calana", "rosa.quispe", "ClaveVieja-1", "ClaveNueva-2");

        assertThat(usuarios.buscarPorNombre("chacra_calana", "rosa.quispe").orElseThrow().hashClave())
                .isEqualTo("hash:ClaveNueva-2");
    }

    @Test
    void con_la_clave_actual_equivocada_no_se_cambia() {
        assertThatThrownBy(() -> servicio.cambiar("chacra_calana", "rosa.quispe", "otra", "ClaveNueva-2"))
                .isInstanceOf(DominioException.class).hasMessage("La contraseña actual no es correcta");
        assertThat(usuarios.buscarPorNombre("chacra_calana", "rosa.quispe").orElseThrow().hashClave())
                .isEqualTo("hash:ClaveVieja-1");
    }

    @Test
    void la_nueva_cumple_la_politica_y_no_repite_la_actual() {
        assertThatThrownBy(() -> servicio.cambiar("chacra_calana", "rosa.quispe", "ClaveVieja-1", "corta"))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> servicio.cambiar("chacra_calana", "rosa.quispe", "ClaveVieja-1", "ClaveVieja-1"))
                .isInstanceOf(DominioException.class).hasMessageContaining("distinta");
    }

    @Test
    void el_administrador_de_la_plataforma_no_la_cambia_aqui() {
        assertThatThrownBy(() -> servicio.cambiar(null, "admin", "x", "ClaveNueva-2"))
                .isInstanceOf(ConflictoDominioException.class);
    }
}
