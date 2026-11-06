package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;

/** Monto en soles con 2 decimales. Todo cálculo de dinero del sistema pasa por esta clase. */
public record Dinero(BigDecimal monto) implements Comparable<Dinero> {

    public static final Dinero CERO = new Dinero(BigDecimal.ZERO);

    public Dinero {
        if (monto == null) {
            throw new DominioException("El monto es obligatorio");
        }
        monto = monto.setScale(2, RoundingMode.HALF_UP);
    }

    public static Dinero de(String monto) {
        return new Dinero(new BigDecimal(monto));
    }

    public Dinero sumar(Dinero otro) {
        return new Dinero(monto.add(otro.monto));
    }

    public Dinero restar(Dinero otro) {
        return new Dinero(monto.subtract(otro.monto));
    }

    /** Por ejemplo, precio por kg multiplicado por los kg. Redondea una sola vez, al final. */
    public Dinero multiplicar(BigDecimal factor) {
        return new Dinero(monto.multiply(factor));
    }

    public boolean esNegativo() {
        return monto.signum() < 0;
    }

    @Override
    public int compareTo(Dinero otro) {
        return monto.compareTo(otro.monto);
    }
}
