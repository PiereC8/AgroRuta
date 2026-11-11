package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;

public record Lote(Long idLote, String productoLote, BigDecimal cantidadKg, LocalDate fechaCosecha,
                   String chacraOrigen, EstadoLote estadoLote, String usuarioRegistro, Instant fechaRegistro) {

    public static final BigDecimal MAXIMO_KG = new BigDecimal("9999999.999");

    public Lote {
        if (productoLote == null || productoLote.strip().length() < 2 || productoLote.strip().length() > 80) {
            throw new DominioException("El producto es obligatorio y tiene de 2 a 80 caracteres");
        }
        productoLote = productoLote.strip();
        if (chacraOrigen == null || chacraOrigen.strip().length() < 2 || chacraOrigen.strip().length() > 120) {
            throw new DominioException("La chacra de origen es obligatoria y tiene de 2 a 120 caracteres");
        }
        chacraOrigen = chacraOrigen.strip();
        if (cantidadKg == null || cantidadKg.signum() <= 0) {
            throw new DominioException("La cantidad en kg debe ser mayor que cero");
        }
        if (cantidadKg.compareTo(MAXIMO_KG) > 0) {
            throw new DominioException("La cantidad en kg no puede superar " + MAXIMO_KG.toPlainString());
        }
        if (cantidadKg.stripTrailingZeros().scale() > 3) {
            throw new DominioException("La cantidad en kg admite hasta 3 decimales");
        }
        cantidadKg = cantidadKg.setScale(3, RoundingMode.UNNECESSARY);
        if (fechaCosecha == null) {
            throw new DominioException("La fecha de cosecha es obligatoria");
        }
        if (estadoLote == null || usuarioRegistro == null || usuarioRegistro.isBlank() || fechaRegistro == null) {
            throw new DominioException("El lote necesita estado, usuario y fecha de registro");
        }
    }

    public static Lote nuevo(String productoLote, BigDecimal cantidadKg, LocalDate fechaCosecha,
                             String chacraOrigen, String usuarioRegistro, Instant ahora, LocalDate hoy) {
        if (fechaCosecha != null && fechaCosecha.isAfter(hoy)) {
            throw new DominioException("La fecha de cosecha no puede ser futura");
        }
        return new Lote(null, productoLote, cantidadKg, fechaCosecha, chacraOrigen, EstadoLote.REGISTRADO,
                usuarioRegistro, ahora);
    }

    public Lote reservar() {
        return conEstado(EstadoLote.REGISTRADO, EstadoLote.RESERVADO);
    }

    public Lote despachar() {
        return conEstado(EstadoLote.RESERVADO, EstadoLote.DESPACHADO);
    }

    public Lote entregar() {
        return conEstado(EstadoLote.DESPACHADO, EstadoLote.ENTREGADO);
    }

    public Lote liquidar() {
        return conEstado(EstadoLote.ENTREGADO, EstadoLote.LIQUIDADO);
    }

    /** Al cancelar el envío el lote vuelve a estar disponible, haya salido o no. */
    public Lote liberar() {
        if (estadoLote != EstadoLote.RESERVADO && estadoLote != EstadoLote.DESPACHADO) {
            throw new ConflictoDominioException("El lote " + idLote + " está " + estadoLote
                    + " y solo se libera si estaba reservado o en camino");
        }
        return new Lote(idLote, productoLote, cantidadKg, fechaCosecha, chacraOrigen, EstadoLote.REGISTRADO,
                usuarioRegistro, fechaRegistro);
    }

    private Lote conEstado(EstadoLote requerido, EstadoLote nuevo) {
        if (estadoLote != requerido) {
            throw new ConflictoDominioException(
                    "El lote " + idLote + " está " + estadoLote + " y debe estar " + requerido);
        }
        return new Lote(idLote, productoLote, cantidadKg, fechaCosecha, chacraOrigen, nuevo, usuarioRegistro,
                fechaRegistro);
    }

    public Lote conId(Long nuevoIdLote) {
        return new Lote(nuevoIdLote, productoLote, cantidadKg, fechaCosecha, chacraOrigen, estadoLote,
                usuarioRegistro, fechaRegistro);
    }
}
