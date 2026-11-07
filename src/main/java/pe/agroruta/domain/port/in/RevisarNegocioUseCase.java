package pe.agroruta.domain.port.in;

public interface RevisarNegocioUseCase {

    /**
     * Avisa de lo que pasó hace poco: entregas con pérdida o merma alta, pagos recibidos y alertas de envíos.
     * Repetirlo no repite los correos.
     */
    void revisarRecientes();

    /** Avisa de comprobantes rechazados o atascados y de cobros vencidos. Repetirlo no repite los correos. */
    void revisarPendientes();

    /** Manda el resumen de la semana que terminó el domingo. Repetirlo no repite el correo. */
    void enviarResumenSemanal();
}
