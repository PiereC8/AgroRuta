package pe.agroruta.application.service;

import java.util.List;

import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.DestinoNoEncontradoException;
import pe.agroruta.domain.model.DestinoVenta;
import pe.agroruta.domain.model.DestinoYaExisteException;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.port.in.AdministrarDestinosUseCase;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;
import pe.agroruta.domain.port.out.PreciosDestinoPort;
import pe.agroruta.domain.port.out.UbicacionDestinoPort;

public class AdministrarDestinosService implements AdministrarDestinosUseCase {

    private final DestinoRepositoryPort destinos;
    private final PreciosDestinoPort precios;
    private final UbicacionDestinoPort ubicaciones;

    public AdministrarDestinosService(DestinoRepositoryPort destinos, PreciosDestinoPort precios,
                                      UbicacionDestinoPort ubicaciones) {
        this.destinos = destinos;
        this.precios = precios;
        this.ubicaciones = ubicaciones;
    }

    @Override
    public DestinoVenta registrarDestino(String nombreDestino, String ciudadDestino, boolean esLocal) {
        DestinoVenta destino = DestinoVenta.nuevo(nombreDestino, ciudadDestino, esLocal);
        if (destinos.existePorNombre(destino.nombreDestino())) {
            throw new DestinoYaExisteException(destino.nombreDestino());
        }
        return destinos.guardar(destino);
    }

    @Override
    public List<DestinoVenta> listarDestinos() {
        return destinos.listarActivos();
    }

    @Override
    public void fijarPrecioEsperado(Long idDestinoVenta, String productoLote, Dinero precioPorKg) {
        obtener(idDestinoVenta);
        if (productoLote == null || productoLote.strip().length() < 2 || productoLote.strip().length() > 80) {
            throw new DominioException("El producto es obligatorio y tiene de 2 a 80 caracteres");
        }
        if (precioPorKg == null || !precioPorKg.esPositivo()) {
            throw new DominioException("El precio esperado por kg debe ser mayor que cero");
        }
        precios.guardarPrecioEsperado(idDestinoVenta, productoLote.strip(), precioPorKg);
    }

    @Override
    public void fijarTarifaFlete(Long idDestinoVenta, Dinero costoFletePorKg) {
        DestinoVenta destino = obtener(idDestinoVenta);
        if (destino.esLocal()) {
            throw new DominioException("Un destino local no paga flete");
        }
        if (costoFletePorKg == null || costoFletePorKg.esNegativo()) {
            throw new DominioException("El flete por kg no puede ser negativo");
        }
        precios.guardarTarifaFlete(idDestinoVenta, costoFletePorKg);
    }

    @Override
    public void fijarCoordenadas(Long idDestinoVenta, double latitud, double longitud) {
        obtener(idDestinoVenta);
        Coordenada coordenada = new Coordenada(latitud, longitud);
        if (!coordenada.dentroDelPeru()) {
            throw new DominioException("La ubicación del destino debe estar dentro del Perú");
        }
        ubicaciones.guardarCoordenada(idDestinoVenta, coordenada);
    }

    private DestinoVenta obtener(Long idDestinoVenta) {
        return destinos.buscarPorId(idDestinoVenta)
                .filter(DestinoVenta::destinoActivo)
                .orElseThrow(() -> new DestinoNoEncontradoException(idDestinoVenta));
    }
}
