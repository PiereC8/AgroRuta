package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.Envio;

public interface DespacharEnvioUseCase {

    /** Reserva el lote del pedido y le asigna un vehículo (y con él, su transportista). */
    Envio despachar(Long idPedido, Long idVehiculo, String usuario);

    /** El vehículo sale: el envío pasa a EN_RUTA y el lote a DESPACHADO. */
    Envio iniciarRuta(Long idEnvio, String usuario);

    List<Envio> listar(int pagina, int tamano);

    Envio obtener(Long idEnvio);
}
