package pe.agroruta.application.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

import pe.agroruta.application.service.Falsos.TransporteEnMemoria;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.Transportista;

class AdministrarTransporteServiceTest {

    private final TransporteEnMemoria transporte = new TransporteEnMemoria();
    private final AdministrarTransporteService servicio = new AdministrarTransporteService(transporte);

    @Test
    void editaElNombreYElTelefonoDelTransportista() {
        Transportista registrado = servicio.registrarTransportista("Juan Condori", "963 118 245");

        Transportista editado = servicio.editarTransportista(registrado.idTransportista(), " Juan Condori Ticona ",
                "952 000 111");

        assertThat(editado.nombreTransportista()).isEqualTo("Juan Condori Ticona");
        assertThat(transporte.buscarTransportista(registrado.idTransportista()).orElseThrow().telefonoTransportista())
                .isEqualTo("952 000 111");
    }

    @Test
    void permiteQuitarElTelefono() {
        Transportista registrado = servicio.registrarTransportista("Rosa Choque", "951 604 392");

        assertThat(servicio.editarTransportista(registrado.idTransportista(), "Rosa Choque", " ")
                .telefonoTransportista()).isNull();
    }

    @Test
    void rechazaUnTelefonoInvalidoSinCambiarNada() {
        Transportista registrado = servicio.registrarTransportista("Luis Apaza", "974 225 618");

        assertThatThrownBy(() -> servicio.editarTransportista(registrado.idTransportista(), "Luis Apaza", "abc"))
                .isInstanceOf(DominioException.class);
        assertThat(transporte.buscarTransportista(registrado.idTransportista()).orElseThrow().telefonoTransportista())
                .isEqualTo("974 225 618");
    }

    @Test
    void noEditaUnTransportistaQueNoExiste() {
        assertThatThrownBy(() -> servicio.editarTransportista(99L, "Nadie", null))
                .isInstanceOf(RecursoNoEncontradoException.class);
    }
}
