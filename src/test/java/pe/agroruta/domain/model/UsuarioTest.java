package pe.agroruta.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class UsuarioTest {

    private static final Instant AHORA = Instant.parse("2026-11-06T15:00:00Z");

    private Usuario usuario() {
        return Usuario.nuevo("dueno.demo", "hash", Rol.DUENO);
    }

    @Test
    void cuatro_fallos_no_bloquean_y_el_quinto_si() {
        Usuario u = usuario();
        for (int i = 0; i < 4; i++) {
            u = u.conIntentoFallido(AHORA);
        }
        assertThat(u.estaBloqueado(AHORA)).isFalse();
        assertThat(u.intentosFallidosSesion()).isEqualTo(4);

        u = u.conIntentoFallido(AHORA);

        assertThat(u.estaBloqueado(AHORA)).isTrue();
        assertThat(u.fechaBloqueoHasta()).isEqualTo(AHORA.plus(Usuario.DURACION_BLOQUEO));
        assertThat(u.intentosFallidosSesion()).isZero();
    }

    @Test
    void el_bloqueo_dura_exactamente_quince_minutos() {
        Usuario u = usuario();
        for (int i = 0; i < 5; i++) {
            u = u.conIntentoFallido(AHORA);
        }

        assertThat(u.estaBloqueado(AHORA.plusSeconds(14 * 60 + 59))).isTrue();
        assertThat(u.estaBloqueado(AHORA.plusSeconds(15 * 60))).isFalse();
    }

    @Test
    void un_acceso_exitoso_limpia_fallos_y_bloqueo() {
        Usuario u = usuario().conIntentoFallido(AHORA).conIntentoFallido(AHORA).conAccesoExitoso();

        assertThat(u.intentosFallidosSesion()).isZero();
        assertThat(u.fechaBloqueoHasta()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "ab", "con espacio", "x`; DROP", "aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa"})
    void rechaza_nombres_de_usuario_invalidos(String nombre) {
        assertThatThrownBy(() -> Usuario.nuevo(nombre, "hash", Rol.OPERADOR)).isInstanceOf(DominioException.class);
    }

    @Test
    void la_politica_de_clave_exige_entre_8_y_72_caracteres() {
        assertThatThrownBy(() -> PoliticaClave.validar("1234567")).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> PoliticaClave.validar(null)).isInstanceOf(DominioException.class);
        assertThatThrownBy(() -> PoliticaClave.validar("a".repeat(73))).isInstanceOf(DominioException.class);
        PoliticaClave.validar("12345678");
        PoliticaClave.validar("a".repeat(72));
    }
}
