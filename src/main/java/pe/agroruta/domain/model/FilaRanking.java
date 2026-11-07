package pe.agroruta.domain.model;

import java.math.BigDecimal;

/** Una fila de un ranking (un destino, un producto o un comprador). El margen es nulo si no hubo ingreso. */
public record FilaRanking(String nombre, int entregas, Dinero ganancia, BigDecimal margenPorcentaje,
                          BigDecimal mermaKg) {
}
