package pe.agroruta.infrastructure.adapter.out.seguridad;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** RFF-09: el token de los enlaces se guarda cifrado con AES-GCM y solo se lee con el mismo secreto. */
class CifradoAesAdapterTest {

    private final CifradoAesAdapter cifrado = new CifradoAesAdapter("secreto-de-prueba-de-al-menos-32-caracteres");

    @Test
    void cifra_y_descifra_sin_dejar_el_token_a_la_vista() {
        String token = "Tk3n-de_ejemplo_43caracteres_aaaaaaaaaaaaaa";

        String guardado = cifrado.cifrar(token);

        assertThat(guardado).doesNotContain(token).hasSizeLessThanOrEqualTo(255);
        assertThat(cifrado.descifrar(guardado)).contains(token);
    }

    @Test
    void cada_vez_cifra_distinto_aunque_el_token_sea_el_mismo() {
        assertThat(cifrado.cifrar("mismo")).isNotEqualTo(cifrado.cifrar("mismo"));
    }

    @Test
    void con_otro_secreto_o_un_dato_alterado_no_descifra() {
        String guardado = cifrado.cifrar("token");
        var otro = new CifradoAesAdapter("otro-secreto-distinto-de-al-menos-32-caracteres");
        char ultimo = guardado.charAt(guardado.length() - 1);
        String alterado = guardado.substring(0, guardado.length() - 1) + (ultimo == 'A' ? 'B' : 'A');

        assertThat(otro.descifrar(guardado)).isEmpty();
        assertThat(cifrado.descifrar(alterado)).isEmpty();
        assertThat(cifrado.descifrar("no-es-base64-%%%")).isEmpty();
    }
}
