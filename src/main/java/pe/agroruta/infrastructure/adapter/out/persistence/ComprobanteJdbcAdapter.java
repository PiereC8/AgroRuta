package pe.agroruta.infrastructure.adapter.out.persistence;

import java.sql.Date;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Comprobante;
import pe.agroruta.domain.model.EstadoComprobante;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.port.out.ComprobanteRepositoryPort;

@Component
public class ComprobanteJdbcAdapter implements ComprobanteRepositoryPort {

    private static final String COLUMNAS = "id_comprobante, id_pago, tipo_comprobante, serie, numero, "
            + "estado_comprobante, enlace_pdf, enlace_xml, mensaje_sunat, intentos, proximo_intento, "
            + "fecha_emision, fecha_creacion";

    private static final RowMapper<Comprobante> FILA = (rs, fila) -> new Comprobante(
            rs.getLong("id_comprobante"),
            rs.getLong("id_pago"),
            TipoComprobante.valueOf(rs.getString("tipo_comprobante")),
            rs.getString("serie"),
            rs.getInt("numero"),
            EstadoComprobante.valueOf(rs.getString("estado_comprobante")),
            rs.getString("enlace_pdf"),
            rs.getString("enlace_xml"),
            rs.getString("mensaje_sunat"),
            rs.getInt("intentos"),
            Sql.instante(rs.getTimestamp("proximo_intento")),
            rs.getDate("fecha_emision").toLocalDate(),
            Sql.instante(rs.getTimestamp("fecha_creacion")));

    private final JdbcTemplate jdbc;

    public ComprobanteJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public Comprobante guardarNuevo(Comprobante c) {
        long id = Sql.insertar(jdbc, "INSERT INTO comprobante (id_pago, tipo_comprobante, serie, numero, "
                        + "estado_comprobante, enlace_pdf, enlace_xml, mensaje_sunat, intentos, proximo_intento, "
                        + "fecha_emision, fecha_creacion) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)",
                c.idPago(), c.tipoComprobante().name(), c.serie(), c.numero(), c.estadoComprobante().name(),
                c.enlacePdf(), c.enlaceXml(), c.mensajeSunat(), c.intentos(), Sql.marca(c.proximoIntento()),
                Date.valueOf(c.fechaEmision()), Sql.marca(c.fechaCreacion()));
        return c.conId(id);
    }

    @Override
    public void actualizar(Comprobante c) {
        jdbc.update("UPDATE comprobante SET estado_comprobante = ?, enlace_pdf = ?, enlace_xml = ?, "
                        + "mensaje_sunat = ?, intentos = ?, proximo_intento = ? WHERE id_comprobante = ?",
                c.estadoComprobante().name(), c.enlacePdf(), c.enlaceXml(), recortar(c.mensajeSunat(), 500),
                c.intentos(), Sql.marca(c.proximoIntento()), c.idComprobante());
    }

    @Override
    public Optional<Comprobante> buscarPorId(Long idComprobante) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM comprobante WHERE id_comprobante = ?", FILA, idComprobante)
                .stream().findFirst();
    }

    @Override
    public Optional<Comprobante> buscarPorPago(Long idPago) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM comprobante WHERE id_pago = ?", FILA, idPago)
                .stream().findFirst();
    }

    @Override
    public List<Comprobante> pendientesParaReintento(Instant ahora, int limite) {
        return jdbc.query("SELECT " + COLUMNAS + " FROM comprobante WHERE estado_comprobante = 'PENDIENTE' "
                + "AND proximo_intento IS NOT NULL AND proximo_intento <= ? ORDER BY proximo_intento LIMIT ?",
                FILA, Sql.marca(ahora), limite);
    }

    private static String recortar(String texto, int maximo) {
        return texto == null || texto.length() <= maximo ? texto : texto.substring(0, maximo);
    }
}
