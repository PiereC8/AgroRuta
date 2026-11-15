package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.TipoComprador;

public interface AdministrarCompradoresUseCase {

    Comprador registrar(String nombreComprador, TipoComprador tipoComprador, String documentoComprador,
                        String telefonoComprador, String ciudadComprador, String correoComprador);

    default Comprador registrar(String nombreComprador, TipoComprador tipoComprador, String documentoComprador,
                                String telefonoComprador, String ciudadComprador) {
        return registrar(nombreComprador, tipoComprador, documentoComprador, telefonoComprador, ciudadComprador, null);
    }

    /** Corrige los datos de un comprador (por ejemplo, agregarle el DNI o RUC para poder cobrarle). */
    Comprador actualizar(Long idComprador, String nombreComprador, TipoComprador tipoComprador,
                         String documentoComprador, String telefonoComprador, String ciudadComprador,
                         String correoComprador);

    default Comprador actualizar(Long idComprador, String nombreComprador, TipoComprador tipoComprador,
                                 String documentoComprador, String telefonoComprador, String ciudadComprador) {
        return actualizar(idComprador, nombreComprador, tipoComprador, documentoComprador, telefonoComprador,
                ciudadComprador, null);
    }

    List<Comprador> listar();
}
