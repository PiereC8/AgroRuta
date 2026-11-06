package pe.agroruta.domain.model;

public record DestinoVenta(Long idDestinoVenta, String nombreDestino, String ciudadDestino, boolean esLocal,
                           boolean destinoActivo) {

    public DestinoVenta {
        if (nombreDestino == null || nombreDestino.strip().length() < 2 || nombreDestino.strip().length() > 80) {
            throw new DominioException("El nombre del destino es obligatorio y tiene de 2 a 80 caracteres");
        }
        nombreDestino = nombreDestino.strip();
        if (ciudadDestino == null || ciudadDestino.strip().length() < 2 || ciudadDestino.strip().length() > 80) {
            throw new DominioException("La ciudad del destino es obligatoria y tiene de 2 a 80 caracteres");
        }
        ciudadDestino = ciudadDestino.strip();
    }

    public static DestinoVenta nuevo(String nombreDestino, String ciudadDestino, boolean esLocal) {
        return new DestinoVenta(null, nombreDestino, ciudadDestino, esLocal, true);
    }

    public DestinoVenta conId(Long nuevoIdDestinoVenta) {
        return new DestinoVenta(nuevoIdDestinoVenta, nombreDestino, ciudadDestino, esLocal, destinoActivo);
    }
}
