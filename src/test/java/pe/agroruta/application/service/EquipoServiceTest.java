package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.Falsos.HashFalso;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.MiembroEquipo;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.out.EquipoRepositoryPort;

class EquipoServiceTest {

    static class EquipoEnMemoria implements EquipoRepositoryPort {
        final List<MiembroEquipo> miembros = new ArrayList<>();
        String ultimoHash;

        @Override
        public List<MiembroEquipo> listar() {
            return List.copyOf(miembros);
        }

        @Override
        public Optional<MiembroEquipo> buscar(Long idUsuario) {
            return miembros.stream().filter(m -> m.idUsuario().equals(idUsuario)).findFirst();
        }

        @Override
        public boolean existePorNombre(String nombreUsuario) {
            return miembros.stream().anyMatch(m -> m.nombreUsuario().equals(nombreUsuario));
        }

        @Override
        public MiembroEquipo crearOperador(String nombreUsuario, String hashClave, String correo) {
            ultimoHash = hashClave;
            MiembroEquipo nuevo = new MiembroEquipo((long) miembros.size() + 1, nombreUsuario, Rol.OPERADOR, correo,
                    true, true);
            miembros.add(nuevo);
            return nuevo;
        }

        @Override
        public void actualizarCorreo(Long idUsuario, String correo, boolean avisosActivos) {
            MiembroEquipo m = buscar(idUsuario).orElseThrow();
            miembros.set(miembros.indexOf(m), new MiembroEquipo(m.idUsuario(), m.nombreUsuario(), m.rol(), correo,
                    avisosActivos, m.activo()));
        }

        @Override
        public void actualizarActivo(Long idUsuario, boolean activo) {
            MiembroEquipo m = buscar(idUsuario).orElseThrow();
            miembros.set(miembros.indexOf(m), new MiembroEquipo(m.idUsuario(), m.nombreUsuario(), m.rol(),
                    m.correo(), m.avisosActivos(), activo));
        }

        @Override
        public List<MiembroEquipo> destinatarios(Set<Rol> roles) {
            return miembros.stream().filter(m -> roles.contains(m.rol()) && m.puedeRecibirAvisos()).toList();
        }
    }

    private EquipoEnMemoria equipo;
    private EquipoService servicio;

    @BeforeEach
    void preparar() {
        equipo = new EquipoEnMemoria();
        equipo.miembros.add(new MiembroEquipo(1L, "dueno.demo", Rol.DUENO, null, true, true));
        servicio = new EquipoService(equipo, new HashFalso());
    }

    @Test
    void crea_un_operador_con_la_clave_cifrada_y_el_correo_en_minusculas() {
        MiembroEquipo creado = servicio.crearOperador("operador.uno", "clave-segura-1", " Ana@Correo.PE ");

        assertThat(creado.rol()).isEqualTo(Rol.OPERADOR);
        assertThat(creado.correo()).isEqualTo("ana@correo.pe");
        assertThat(equipo.ultimoHash).isEqualTo("hash:clave-segura-1");
    }

    @Test
    void el_correo_es_opcional_al_crear_un_operador() {
        assertThat(servicio.crearOperador("operador.uno", "clave-segura-1", "  ").correo()).isNull();
    }

    @Test
    void no_crea_un_operador_con_nombre_repetido_ni_clave_corta_ni_correo_malo() {
        assertThatThrownBy(() -> servicio.crearOperador("dueno.demo", "clave-segura-1", null))
                .isInstanceOf(ConflictoDominioException.class);
        assertThatThrownBy(() -> servicio.crearOperador("operador.uno", "corta", null))
                .isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> servicio.crearOperador("operador.uno", "clave-segura-1", "no-es-correo"))
                .isInstanceOf(DominioException.class).hasMessageContaining("correo");
        assertThat(equipo.miembros).hasSize(1);
    }

    @Test
    void cambia_el_correo_y_los_avisos_de_un_miembro() {
        servicio.cambiarCorreo(1L, "Dueno@Empresa.pe", true);

        MiembroEquipo dueno = equipo.buscar(1L).orElseThrow();
        assertThat(dueno.correo()).isEqualTo("dueno@empresa.pe");
        assertThat(dueno.puedeRecibirAvisos()).isTrue();
    }

    @Test
    void sin_correo_no_se_pueden_activar_los_avisos() {
        assertThatThrownBy(() -> servicio.cambiarCorreo(1L, null, true))
                .isInstanceOf(DominioException.class).hasMessageContaining("correo");
        servicio.cambiarCorreo(1L, null, false);
        assertThat(equipo.buscar(1L).orElseThrow().puedeRecibirAvisos()).isFalse();
    }

    @Test
    void un_miembro_que_no_existe_da_no_encontrado() {
        assertThatThrownBy(() -> servicio.cambiarCorreo(99L, "a@b.pe", true))
                .isInstanceOf(RecursoNoEncontradoException.class);
        assertThatThrownBy(() -> servicio.cambiarActivo(99L, false, "dueno.demo"))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }

    @Test
    void se_puede_desactivar_y_reactivar_a_un_operador() {
        MiembroEquipo operador = servicio.crearOperador("operador.uno", "clave-segura-1", "op@correo.pe");

        servicio.cambiarActivo(operador.idUsuario(), false, "dueno.demo");
        assertThat(equipo.buscar(operador.idUsuario()).orElseThrow().activo()).isFalse();
        servicio.cambiarActivo(operador.idUsuario(), true, "dueno.demo");
        assertThat(equipo.buscar(operador.idUsuario()).orElseThrow().activo()).isTrue();
    }

    @Test
    void nadie_puede_desactivarse_a_si_mismo() {
        assertThatThrownBy(() -> servicio.cambiarActivo(1L, false, "dueno.demo"))
                .isInstanceOf(DominioException.class).hasMessageContaining("a ti mismo");
    }

    @Test
    void un_miembro_desactivado_no_recibe_avisos() {
        MiembroEquipo operador = servicio.crearOperador("operador.uno", "clave-segura-1", "op@correo.pe");
        servicio.cambiarActivo(operador.idUsuario(), false, "dueno.demo");

        assertThat(equipo.destinatarios(Set.of(Rol.OPERADOR))).isEmpty();
    }
}
