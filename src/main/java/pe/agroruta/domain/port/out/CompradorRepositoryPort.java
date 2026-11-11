package pe.agroruta.domain.port.out;

import java.util.List;
import java.util.Optional;

import pe.agroruta.domain.model.Comprador;

public interface CompradorRepositoryPort {

    boolean existePorDocumento(String documentoComprador);

    Comprador guardar(Comprador comprador);

    void actualizar(Comprador comprador);

    Optional<Comprador> buscarPorId(Long idComprador);

    List<Comprador> listarActivos();
}
