package pe.agroruta.domain.model;

public final class Paginacion {

    public static final int TAMANO_MAXIMO = 200;

    private Paginacion() {
    }

    public static void validar(int pagina, int tamano) {
        if (pagina < 0 || tamano < 1 || tamano > TAMANO_MAXIMO) {
            throw new DominioException("La página empieza en 0 y el tamaño va de 1 a " + TAMANO_MAXIMO);
        }
    }
}
