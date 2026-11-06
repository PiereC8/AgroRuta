package pe.agroruta.domain.model;

/** Lo que un enlace con token permite: un solo envío de un solo negocio, con un solo papel. */
public record SesionEnlace(String identificadorNegocio, Long idEnvio, TipoEnlace tipoEnlace) {
}
