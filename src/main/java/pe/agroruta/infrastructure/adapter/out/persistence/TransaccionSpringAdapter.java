package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import pe.agroruta.domain.port.out.TransaccionPort;

@Component
public class TransaccionSpringAdapter implements TransaccionPort {

    private final TransactionTemplate plantilla;

    public TransaccionSpringAdapter(@Qualifier("transaccionesNegocios") PlatformTransactionManager transacciones) {
        this.plantilla = new TransactionTemplate(transacciones);
    }

    @Override
    public <T> T ejecutar(Supplier<T> accion) {
        return plantilla.execute(estado -> accion.get());
    }
}
