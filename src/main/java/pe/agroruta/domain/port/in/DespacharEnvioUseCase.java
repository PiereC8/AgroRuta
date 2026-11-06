package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.Envio;

public interface DespacharEnvioUseCase {

    Envio despachar(Long idPedido, Long idVehiculo, String usuario);

    Envio iniciarRuta(Long idEnvio, String usuario);

    List<Envio> listar(int pagina, int tamano);

    Envio obtener(Long idEnvio);
}
