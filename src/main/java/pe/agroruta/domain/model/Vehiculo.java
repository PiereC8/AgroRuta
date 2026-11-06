package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.regex.Pattern;

public record Vehiculo(Long idVehiculo, Long idTransportista, String placaVehiculo, BigDecimal capacidadKg,
                       boolean vehiculoActivo) {

    public static final BigDecimal CAPACIDAD_MAXIMA_KG = new BigDecimal("100000");

    private static final Pattern FORMATO_PLACA = Pattern.compile("^[A-Z0-9]{3}-?[A-Z0-9]{3,4}$");

    public Vehiculo {
        if (idTransportista == null) {
            throw new DominioException("El vehículo necesita un transportista");
        }
        if (placaVehiculo == null) {
            throw new DominioException("La placa es obligatoria");
        }
        placaVehiculo = placaVehiculo.strip().toUpperCase();
        if (!FORMATO_PLACA.matcher(placaVehiculo).matches()) {
            throw new DominioException("La placa debe tener el formato ABC-123");
        }
        if (capacidadKg == null || capacidadKg.signum() <= 0 || capacidadKg.compareTo(CAPACIDAD_MAXIMA_KG) > 0) {
            throw new DominioException("La capacidad va de más de 0 a " + CAPACIDAD_MAXIMA_KG.toPlainString() + " kg");
        }
        if (capacidadKg.stripTrailingZeros().scale() > 3) {
            throw new DominioException("La capacidad admite hasta 3 decimales");
        }
        capacidadKg = capacidadKg.setScale(3, RoundingMode.UNNECESSARY);
    }

    public static Vehiculo nuevo(Long idTransportista, String placaVehiculo, BigDecimal capacidadKg) {
        return new Vehiculo(null, idTransportista, placaVehiculo, capacidadKg, true);
    }

    public void verificarCapacidad(BigDecimal cargaKg) {
        if (cargaKg.compareTo(capacidadKg) > 0) {
            throw new DominioException("El lote pesa " + cargaKg.toPlainString() + " kg y el vehículo "
                    + placaVehiculo + " carga hasta " + capacidadKg.toPlainString() + " kg");
        }
    }

    public Vehiculo conId(Long nuevoIdVehiculo) {
        return new Vehiculo(nuevoIdVehiculo, idTransportista, placaVehiculo, capacidadKg, vehiculoActivo);
    }
}
