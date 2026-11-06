package pe.agroruta.infrastructure.adapter.out.persistence;

import java.time.Instant;
import java.util.Optional;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.EnlaceEnvio;
import pe.agroruta.domain.model.TipoEnlace;
import pe.agroruta.domain.port.out.EnlaceEnvioRepositoryPort;

@Component
public class EnlaceEnvioJdbcAdapter implements EnlaceEnvioRepositoryPort {

    private final JdbcTemplate jdbc;

    public EnlaceEnvioJdbcAdapter(@Qualifier("plataformaJdbc") JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void guardar(EnlaceEnvio enlace) {
        jdbc.update("INSERT INTO enlace_envio (hash_token, tipo_enlace, identificador_negocio, id_envio, "
                        + "fecha_expiracion) VALUES (?, ?, ?, ?, ?)",
                enlace.hashToken(), enlace.tipoEnlace().name(), enlace.identificadorNegocio(), enlace.idEnvio(),
                Sql.marca(enlace.fechaExpiracion()));
    }

    @Override
    public void desactivarDeEnvio(String identificadorNegocio, Long idEnvio) {
        jdbc.update("UPDATE enlace_envio SET enlace_activo = FALSE WHERE identificador_negocio = ? AND id_envio = ?",
                identificadorNegocio, idEnvio);
    }

    @Override
    public Optional<EnlaceEnvio> buscarVigentePorHash(String hashToken, Instant ahora) {
        return jdbc.query("SELECT hash_token, tipo_enlace, identificador_negocio, id_envio, fecha_expiracion "
                        + "FROM enlace_envio WHERE hash_token = ? AND enlace_activo = TRUE AND fecha_expiracion > ?",
                (rs, fila) -> new EnlaceEnvio(rs.getString("identificador_negocio"), rs.getLong("id_envio"),
                        TipoEnlace.valueOf(rs.getString("tipo_enlace")), rs.getString("hash_token"),
                        Sql.instante(rs.getTimestamp("fecha_expiracion"))),
                hashToken, Sql.marca(ahora)).stream().findFirst();
    }
}
