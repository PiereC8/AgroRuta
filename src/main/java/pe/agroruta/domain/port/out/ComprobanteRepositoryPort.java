package pe.agroruta.domain.port.out;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.Comprobante;

public interface ComprobanteRepositoryPort {

    Comprobante guardarNuevo(Comprobante comprobante);

    void actualizar(Comprobante comprobante);

    Optional<Comprobante> buscarPorId(Long idComprobante);

    Optional<Comprobante> buscarPorPago(Long idPago);

    List<Comprobante> pendientesParaReintento(Instant ahora, int limite);
}
