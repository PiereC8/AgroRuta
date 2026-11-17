package pe.agroruta.domain.model;

import java.time.Instant;

// RFF-08 (Despacho): envío de un pedido en un vehículo.
// Estados: programado → en ruta → entregado; se puede cancelar antes de entregar.
public record Envio(Long idEnvio, Long idPedido, Long idLote, Long idVehiculo, Long idTransportista,
                    EstadoEnvio estadoEnvio, Instant fechaProgramada, Instant fechaSalida, Instant fechaEntrega,
                    String usuarioDespacho) {

    public static Envio programar(Pedido pedido, Vehiculo vehiculo, String usuarioDespacho, Instant ahora) {
        if (usuarioDespacho == null || usuarioDespacho.isBlank()) {
            throw new DominioException("El envío necesita el usuario que lo despacha");
        }
        return new Envio(null, pedido.idPedido(), pedido.idLote(), vehiculo.idVehiculo(),
                vehiculo.idTransportista(), EstadoEnvio.PROGRAMADO, ahora, null, null, usuarioDespacho);
    }

    public Envio iniciarRuta(Instant ahora) {
        exigirEstado(EstadoEnvio.PROGRAMADO, "salir en ruta");
        return new Envio(idEnvio, idPedido, idLote, idVehiculo, idTransportista, EstadoEnvio.EN_RUTA,
                fechaProgramada, ahora, null, usuarioDespacho);
    }

    public Envio marcarEntregado(Instant ahora) {
        exigirEstado(EstadoEnvio.EN_RUTA, "entregarse");
        return new Envio(idEnvio, idPedido, idLote, idVehiculo, idTransportista, EstadoEnvio.ENTREGADO,
                fechaProgramada, fechaSalida, ahora, usuarioDespacho);
    }

    public static final int LARGO_MAXIMO_MOTIVO = 255;

    /**
     * Se cancela un envío programado o en ruta (por ejemplo, si el camión falla). Si ya salió, hay que decir por qué:
     * la carga está en el camino y la cancelación queda en el historial con su motivo.
     */
    public Envio cancelar(String motivo) {
        if (estadoEnvio != EstadoEnvio.PROGRAMADO && estadoEnvio != EstadoEnvio.EN_RUTA) {
            throw new ConflictoDominioException("El envío " + idEnvio + " está " + estadoEnvio
                    + " y solo se cancela si está programado o en ruta");
        }
        if (estadoEnvio == EstadoEnvio.EN_RUTA && (motivo == null || motivo.isBlank())) {
            throw new DominioException("El envío ya salió: indica el motivo para cancelarlo");
        }
        if (motivo != null && motivo.strip().length() > LARGO_MAXIMO_MOTIVO) {
            throw new DominioException("El motivo puede tener hasta " + LARGO_MAXIMO_MOTIVO + " caracteres");
        }
        return new Envio(idEnvio, idPedido, idLote, idVehiculo, idTransportista, EstadoEnvio.CANCELADO,
                fechaProgramada, fechaSalida, fechaEntrega, usuarioDespacho);
    }

    /** Una posición solo vale si fue tomada mientras el envío estaba en ruta. */
    public boolean admitePosicionEn(Instant fecha) {
        if (estadoEnvio == EstadoEnvio.CANCELADO || fechaSalida == null || fecha.isBefore(fechaSalida)) {
            return false;
        }
        return fechaEntrega == null || !fecha.isAfter(fechaEntrega);
    }

    public Envio conId(Long nuevoIdEnvio) {
        return new Envio(nuevoIdEnvio, idPedido, idLote, idVehiculo, idTransportista, estadoEnvio,
                fechaProgramada, fechaSalida, fechaEntrega, usuarioDespacho);
    }

    private void exigirEstado(EstadoEnvio esperado, String accion) {
        if (estadoEnvio != esperado) {
            throw new ConflictoDominioException("El envío " + idEnvio + " está " + estadoEnvio
                    + " y solo puede " + accion + " desde " + esperado);
        }
    }
}
