package pe.agroruta.application.service;

import java.util.List;

import pe.agroruta.domain.model.Comprador;
import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.TipoComprador;
import pe.agroruta.domain.port.in.AdministrarCompradoresUseCase;
import pe.agroruta.domain.port.out.CompradorRepositoryPort;

public class AdministrarCompradoresService implements AdministrarCompradoresUseCase {

    private final CompradorRepositoryPort compradores;

    public AdministrarCompradoresService(CompradorRepositoryPort compradores) {
        this.compradores = compradores;
    }

    @Override
    public Comprador registrar(String nombreComprador, TipoComprador tipoComprador, String documentoComprador,
                               String telefonoComprador, String ciudadComprador) {
        Comprador comprador = Comprador.nuevo(nombreComprador, tipoComprador, documentoComprador,
                telefonoComprador, ciudadComprador);
        if (comprador.documentoComprador() != null && compradores.existePorDocumento(comprador.documentoComprador())) {
            throw new ConflictoDominioException(
                    "Ya existe un comprador con el documento " + comprador.documentoComprador());
        }
        return compradores.guardar(comprador);
    }

    @Override
    public List<Comprador> listar() {
        return compradores.listarActivos();
    }
}
