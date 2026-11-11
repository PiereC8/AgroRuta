package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Dinero;
import pe.agroruta.domain.model.EstadoPago;
import pe.agroruta.domain.model.Pago;
import pe.agroruta.domain.port.out.PagoRepositoryPort;

@Component
public class PagoJdbcAdapter implements PagoRepositoryPort {

    private static final String COLUMNAS = "id_pago, id_liquidacion, id_pedido, monto, estado_pago, "
            + "clave_idempotencia, id_cargo_pasarela, codigo_respuesta, mensaje_usuario, marca_tarjeta, "
            + "ultimos_cuatro, email_pagador, fecha_creacion, fecha_actualizacion";

    private static final RowMapper<Pago> FILA = (rs, fila) -> new Pago(
            rs.getLong("id_pago"),
            rs.getLong("id_liquidacion"),
            rs.getLong("id_pedido"),
            new Dinero(rs.getBigDecimal("monto")),
            EstadoPago.valueOf(rs.getString("estado_pago")),
            rs.getString("clave_idempotencia"),
            rs.getString("id_cargo_pasarela"),
            rs.getString("codigo_respuesta"),
            rs.getString("mensaje_usuario"),
            rs.getString("marca_tarjeta"),
            rs.getString("ultimos_cuatro"),
            rs.getString("email_pagador"),
            Sql.instante(rs.getTimestamp("fecha_creacion")),
            Sql.instante(rs.getTimestamp("fecha_actualizacion")));

    private final JdbcTemplate jdbc;

    public PagoJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Pago guardarNuevo(Pago pago) {
        try {
            long id = Sql.insertar(jdbc, "INSERT INTO pago (id_liquidacion, id_pedido, monto, estado_pago, "
                            + "clave_idempotencia, id_cargo_pasarela, codigo_respuesta, mensaje_usuario, marca_tarjeta, "
                            + "ultimos_cuatro, email_pagador, fecha_creacion, fecha_actualizacion) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                    pago.idLiquidacion(), pago.idPedido(), pago.monto().monto(), pago.estadoPago().name(),
                    pago.claveIdempotencia(), pago.idCargoPasarela(), pago.codigoRespuesta(), pago.mensajeUsuario(),
                    pago.marcaTarjeta(), pago.ultimosCuatro(), pago.emailPagador(), Sql.marca(pago.fechaCreacion()),
                    Sql.marca(pago.fechaActualizacion()));
            return pago.conId(id);
        } catch (DuplicateKeyException e) {
            // Otro intento de pago del mismo pedido llegó al mismo tiempo y ganó: este no se cobra.
            throw new ConflictoDominioException(
                    "Ya hay un pago en curso para este pedido. Espera unos segundos y revisa si se aprobó");
        }
    }

    @Override
    public void actualizar(Pago pago) {
        jdbc.update("UPDATE pago SET estado_pago = ?, id_cargo_pasarela = ?, codigo_respuesta = ?, "
                        + "mensaje_usuario = ?, marca_tarjeta = ?, ultimos_cuatro = ?, fecha_actualizacion = ? "
                        + "WHERE id_pago = ?",
                pago.estadoPago().name(), pago.idCargoPasarela(), pago.codigoRespuesta(), pago.mensajeUsuario(),
                pago.marcaTarjeta(), pago.ultimosCuatro(), Sql.marca(pago.fechaActualizacion()), pago.idPago());
    }

    @Override
    public Optional<Pago> buscarPorId(Long idPago) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM pago WHERE id_pago = ?", FILA, idPago).stream().findFirst();
    }

    @Override
    public Optional<Pago> buscarPorClave(String claveIdempotencia) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM pago WHERE clave_idempotencia = ?", FILA, claveIdempotencia)
                .stream().findFirst();
    }

    @Override
    public boolean existeAprobadoDe(Long idLiquidacion) {
        return contar(idLiquidacion, "APROBADO") > 0;
    }

    @Override
    public boolean existePendienteDe(Long idLiquidacion) {
        return contar(idLiquidacion, "PENDIENTE") > 0;
    }

    @Override
    public Optional<Pago> ultimoDeLiquidacion(Long idLiquidacion) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM pago WHERE id_liquidacion = ? "
                + "ORDER BY id_pago DESC LIMIT 1", FILA, idLiquidacion).stream().findFirst();
    }

    @Override
    public List<Pago> listar(int pagina, int tamano) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM pago ORDER BY id_pago DESC LIMIT ? OFFSET ?", FILA, tamano,
                (long) pagina * tamano);
    }

    private int contar(Long idLiquidacion, String estado) {
        Integer total = jdbc.queryForObject("SELECT COUNT(*) FROM pago WHERE id_liquidacion = ? AND estado_pago = ?",
                Integer.class, idLiquidacion, estado);
        return total == null ? 0 : total;
    }
}
