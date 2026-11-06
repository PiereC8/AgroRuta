package pe.agroruta.domain.port.in;

public interface ConfirmarPagoUseCase {

    /**
     * Confirma un cargo a partir de una notificación de la pasarela. La notificación no se cree: se vuelve a
     * preguntar el cargo a la pasarela. Devuelve verdadero si encontró y procesó el pago.
     */
    boolean confirmar(String idCargo);
}
