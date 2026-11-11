package pe.agroruta.infrastructure.adapter.in.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;

class VerificadorUsuarioActivoTest {

    private static final Instant AHORA = Instant.parse("2026-11-11T15:00:00Z");

    private static Usuario usuario(boolean activo) {
        return new Usuario(1L, "julio", "hash", Rol.OPERADOR, 0, null, activo);
    }

    @Test
    void un_usuario_desactivado_ya_no_pasa_aunque_su_token_siga_vigente() {
        UsuarioRepositoryPort usuarios = mock(UsuarioRepositoryPort.class);
        when(usuarios.buscarPorNombre("asoc_yarada", "julio")).thenReturn(Optional.of(usuario(false)));

        var verificador = new VerificadorUsuarioActivo(usuarios, Clock.fixed(AHORA, ZoneId.of("UTC")));

        assertThat(verificador.estaActivo("asoc_yarada", "julio")).isFalse();
    }

    @Test
    void un_usuario_que_ya_no_existe_tampoco_pasa() {
        UsuarioRepositoryPort usuarios = mock(UsuarioRepositoryPort.class);
        when(usuarios.buscarPorNombre("asoc_yarada", "borrado")).thenReturn(Optional.empty());

        var verificador = new VerificadorUsuarioActivo(usuarios, Clock.fixed(AHORA, ZoneId.of("UTC")));

        assertThat(verificador.estaActivo("asoc_yarada", "borrado")).isFalse();
    }

    @Test
    void la_respuesta_se_reutiliza_unos_segundos_y_luego_se_vuelve_a_consultar() {
        UsuarioRepositoryPort usuarios = mock(UsuarioRepositoryPort.class);
        when(usuarios.buscarPorNombre("asoc_yarada", "julio"))
                .thenReturn(Optional.of(usuario(true)), Optional.of(usuario(false)));
        var reloj = new RelojMovible(AHORA);
        var verificador = new VerificadorUsuarioActivo(usuarios, reloj);

        assertThat(verificador.estaActivo("asoc_yarada", "julio")).isTrue();
        reloj.ahora = AHORA.plusSeconds(10);
        assertThat(verificador.estaActivo("asoc_yarada", "julio")).isTrue();
        verify(usuarios, times(1)).buscarPorNombre("asoc_yarada", "julio");

        reloj.ahora = AHORA.plus(VerificadorUsuarioActivo.VIGENCIA).plusSeconds(1);
        assertThat(verificador.estaActivo("asoc_yarada", "julio")).isFalse();
        verify(usuarios, times(2)).buscarPorNombre("asoc_yarada", "julio");
    }

    @Test
    void el_administrador_de_la_plataforma_no_se_busca_en_ningun_negocio() {
        UsuarioRepositoryPort usuarios = mock(UsuarioRepositoryPort.class);
        var verificador = new VerificadorUsuarioActivo(usuarios, Clock.fixed(AHORA, ZoneId.of("UTC")));

        assertThat(verificador.estaActivo(null, "admin")).isTrue();
        verify(usuarios, never()).buscarPorNombre(null, "admin");
    }

    private static final class RelojMovible extends Clock {
        Instant ahora;

        RelojMovible(Instant ahora) {
            this.ahora = ahora;
        }

        @Override
        public ZoneId getZone() {
            return ZoneId.of("UTC");
        }

        @Override
        public Clock withZone(ZoneId zona) {
            return this;
        }

        @Override
        public Instant instant() {
            return ahora;
        }
    }
}
