package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.TipoComprador;

public interface AdministrarCompradoresUseCase {

    Comprador registrar(String nombreComprador, TipoComprador tipoComprador, String documentoComprador,
                        String telefonoComprador, String ciudadComprador);

    /** Corrige los datos de un comprador (por ejemplo, agregarle el DNI o RUC para poder cobrarle). */
    Comprador actualizar(Long idComprador, String nombreComprador, TipoComprador tipoComprador,
                         String documentoComprador, String telefonoComprador, String ciudadComprador);

    List<Comprador> listar();
}
