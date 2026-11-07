package pe.agroruta.domain.model;

/** Cómo va el negocio en un período: si gana, si gana menos que antes, si pierde, o si no hay datos. */
public enum Veredicto {
    GANANDO,
    GANANDO_MENOS,
    PERDIENDO,
    SIN_DATOS
}
