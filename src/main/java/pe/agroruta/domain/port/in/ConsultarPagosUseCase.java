package pe.agroruta.domain.port.in;

import java.util.List;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.Pago;

public interface ConsultarPagosUseCase {

    List<Pago> listar(int pagina, int tamano);

    Pago pagoDeLote(Long idLote);

    Comprobante comprobanteDePago(Long idPago);
}
