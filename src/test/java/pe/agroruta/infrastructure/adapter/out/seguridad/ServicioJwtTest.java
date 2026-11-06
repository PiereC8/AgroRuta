package pe.agroruta.infrastructure.adapter.out.seguridad;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;

import pe.agroruta.domain.model.Rol;

class ServicioJwtTest {

    private static final String SECRETO = "secreto-de-prueba-con-mas-de-32-caracteres!!";
    private static final Instant INICIO = Instant.parse("2026-11-06T15:00:00Z");

    private ServicioJwt servicio(Instant ahora, String secreto) {
        return new ServicioJwt(secreto, Duration.ofHours(8), Clock.fixed(ahora, ZoneOffset.UTC));
    }

    @Test
    void el_token_lleva_usuario_negocio_y_rol() {
        ServicioJwt servicio = servicio(INICIO, SECRETO);

        var datos = servicio.leer(servicio.emitir("chacra_demo", "dueno.demo", Rol.DUENO)).orElseThrow();

        assertThat(datos.nombreUsuario()).isEqualTo("dueno.demo");
        assertThat(datos.identificadorNegocio()).isEqualTo("chacra_demo");
        assertThat(datos.rolUsuario()).isEqualTo(Rol.DUENO);
    }

    @Test
    void el_token_del_administrador_no_lleva_negocio() {
        ServicioJwt servicio = servicio(INICIO, SECRETO);

        var datos = servicio.leer(servicio.emitir(null, "admin", Rol.ADMINISTRADOR_PLATAFORMA)).orElseThrow();

        assertThat(datos.identificadorNegocio()).isNull();
    }

    @Test
    void rechaza_un_token_alterado() {
        ServicioJwt servicio = servicio(INICIO, SECRETO);
        String token = servicio.emitir("chacra_demo", "dueno.demo", Rol.OPERADOR);
        String[] partes = token.split("\\.");
        String alterado = partes[0] + "." + partes[1] + "x." + partes[2];

        assertThat(servicio.leer(alterado)).isEmpty();
        assertThat(servicio.leer("no-es-un-token")).isEmpty();
    }

    @Test
    void rechaza_un_token_firmado_con_otro_secreto() {
        String token = servicio(INICIO, "otro-secreto-distinto-de-mas-de-32-caracteres")
                .emitir("chacra_demo", "dueno.demo", Rol.DUENO);

        assertThat(servicio(INICIO, SECRETO).leer(token)).isEmpty();
    }

    @Test
    void rechaza_un_token_vencido() {
        String token = servicio(INICIO, SECRETO).emitir("chacra_demo", "dueno.demo", Rol.DUENO);

        assertThat(servicio(INICIO.plus(Duration.ofHours(7)), SECRETO).leer(token)).isPresent();
        assertThat(servicio(INICIO.plus(Duration.ofHours(9)), SECRETO).leer(token)).isEmpty();
    }

    @Test
    void exige_un_secreto_largo() {
        assertThatThrownBy(() -> servicio(INICIO, "corto")).isInstanceOf(IllegalStateException.class);
        assertThatThrownBy(() -> servicio(INICIO, null)).isInstanceOf(IllegalStateException.class);
    }
}
