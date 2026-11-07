package pe.agroruta.domain.model;

/** Lo que hay que contar por correo. La clave identifica el hecho: el mismo hecho nunca se avisa dos veces. */
public record Aviso(String clave, TipoAviso tipo, String asunto, String cuerpo) {
}
