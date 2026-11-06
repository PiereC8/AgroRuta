package pe.agroruta.domain.model;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;

/**
 * Resultado de una entrega: cuánto llegó, cuánto se perdió y cuánto se ganó de verdad.
 *
 * <pre>
 * ingreso real     = precio acordado x kg recibidos
 * flete            = tarifa por kg x kg despachados   (se paga por todo lo transportado)
 * ganancia real    = ingreso real - flete
 * ganancia esperada= precio acordado x kg despachados - flete
 * pérdida          = ganancia esperada - ganancia real
 * </pre>
 */
public record Liquidacion(Long idLiquidacion, Long idLote, Long idPedido, Long idEnvio,
                          BigDecimal cantidadDespachadaKg, BigDecimal cantidadRecibidaKg, BigDecimal mermaKg,
                          BigDecimal rechazoKg, Dinero precioAcordadoPorKg, Dinero costoFletePorKg,
                          Dinero ingresoReal, Dinero costoFleteTotal, Dinero gananciaReal, Dinero gananciaEsperada,
                          Dinero perdidaPorMermaYRechazo, String usuarioEntrega, Instant fechaEntrega) {

    public static Liquidacion calcular(Pedido pedido, Envio envio, BigDecimal cantidadRecibidaKg,
                                       BigDecimal mermaKg, BigDecimal rechazoKg, Dinero costoFletePorKg,
                                       String usuarioEntrega, Instant ahora) {
        if (usuarioEntrega == null || usuarioEntrega.isBlank()) {
            throw new DominioException("La entrega necesita el usuario que la registra");
        }
        if (costoFletePorKg == null || costoFletePorKg.esNegativo()) {
            throw new DominioException("El flete por kg no puede ser negativo");
        }
        BigDecimal despachado = pedido.cantidadKg();
        BigDecimal recibido = cantidad("recibida", cantidadRecibidaKg);
        BigDecimal merma = cantidad("de merma", mermaKg);
        BigDecimal rechazo = cantidad("rechazada", rechazoKg);

        BigDecimal suma = recibido.add(merma).add(rechazo);
        if (suma.compareTo(despachado) != 0) {
            throw new DominioException("Lo recibido, la merma y lo rechazado suman " + suma.toPlainString()
                    + " kg, pero lo despachado fue " + despachado.toPlainString() + " kg");
        }

        Dinero precio = pedido.precioAcordadoPorKg();
        Dinero ingresoReal = precio.multiplicar(recibido);
        Dinero costoFlete = costoFletePorKg.multiplicar(despachado);
        Dinero gananciaReal = ingresoReal.restar(costoFlete);
        Dinero gananciaEsperada = precio.multiplicar(despachado).restar(costoFlete);
        return new Liquidacion(null, pedido.idLote(), pedido.idPedido(), envio.idEnvio(), despachado, recibido,
                merma, rechazo, precio, costoFletePorKg, ingresoReal, costoFlete, gananciaReal, gananciaEsperada,
                gananciaEsperada.restar(gananciaReal), usuarioEntrega, ahora);
    }

    private static BigDecimal cantidad(String nombre, BigDecimal valor) {
        if (valor == null || valor.signum() < 0) {
            throw new DominioException("La cantidad " + nombre + " no puede ser negativa ni estar vacía");
        }
        if (valor.stripTrailingZeros().scale() > 3) {
            throw new DominioException("La cantidad " + nombre + " admite hasta 3 decimales");
        }
        return valor.setScale(3, RoundingMode.UNNECESSARY);
    }

    public Liquidacion conId(Long nuevoIdLiquidacion) {
        return new Liquidacion(nuevoIdLiquidacion, idLote, idPedido, idEnvio, cantidadDespachadaKg,
                cantidadRecibidaKg, mermaKg, rechazoKg, precioAcordadoPorKg, costoFletePorKg, ingresoReal,
                costoFleteTotal, gananciaReal, gananciaEsperada, perdidaPorMermaYRechazo, usuarioEntrega,
                fechaEntrega);
    }
}
