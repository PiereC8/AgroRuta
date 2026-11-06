package pe.agroruta.domain.model;

public record Coordenada(double latitud, double longitud) {

    private static final double RADIO_TIERRA_METROS = 6_371_000.0;

    public Coordenada {
        if (Double.isNaN(latitud) || latitud < -90 || latitud > 90) {
            throw new DominioException("La latitud debe estar entre -90 y 90");
        }
        if (Double.isNaN(longitud) || longitud < -180 || longitud > 180) {
            throw new DominioException("La longitud debe estar entre -180 y 180");
        }
    }

    /** Distancia sobre la superficie terrestre (fórmula de haversine). */
    public double distanciaMetrosA(Coordenada otra) {
        double lat1 = Math.toRadians(latitud);
        double lat2 = Math.toRadians(otra.latitud);
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(otra.longitud - longitud);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(lat1) * Math.cos(lat2) * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        return 2 * RADIO_TIERRA_METROS * Math.asin(Math.min(1.0, Math.sqrt(a)));
    }
}
