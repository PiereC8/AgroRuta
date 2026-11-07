package pe.agroruta.domain.port.in;

import pe.agroruta.domain.model.SesionEnlace;

public interface SimularPagoEfectivoUseCase {

    /**
     * Hace como si el comprador hubiera pagado su código de pago (CIP) en un agente, y confirma el pago igual que lo
     * haría la notificación de la pasarela. Solo funciona con la pasarela simulada.
     */
    void simular(SesionEnlace sesion);
}
