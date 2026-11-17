package pe.agroruta.domain.port.out;

import java.util.Optional;

import pe.agroruta.domain.model.DatosFiscales;

public interface DatosFiscalesPort {

    /** Vacío si el negocio no tiene sus datos fiscales completos. */
    Optional<DatosFiscales> buscar(String identificadorNegocio);

    /** false si el negocio no existe. */
    boolean guardar(String identificadorNegocio, DatosFiscales datos);
}
