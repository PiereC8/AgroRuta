package pe.agroruta.domain.port.out;

public interface BaseNegocioPort {

    /** Crea la base propia del negocio y le aplica todas las migraciones. Se puede repetir sin efectos dobles. */
    void crearYMigrar(String slug);
}
