package pe.agroruta.domain.model;

/** Cuánto sumó o restó una causa al cambio de la ganancia real entre dos períodos. */
public record Causa(Concepto concepto, Dinero efecto) {

    public enum Concepto {
        VENTAS,
        MERMA,
        FLETE
    }
}
