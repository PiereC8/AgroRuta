package pe.agroruta.infrastructure.adapter.out.persistence;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

import javax.sql.DataSource;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.Coordenada;
import pe.agroruta.domain.model.Trayecto;
import pe.agroruta.domain.port.out.RutaRepositoryPort;

/** Los puntos se guardan en una sola columna de texto: "lat,lon;lat,lon;..." (la ruta no se consulta por punto). */
@Component
public class RutaJdbcAdapter implements RutaRepositoryPort {

    private final JdbcTemplate jdbc;

    public RutaJdbcAdapter(@Qualifier("negociosDataSource") DataSource negociosDataSource) {
        this.jdbc = new JdbcTemplate(negociosDataSource);
    }

    @Override
    public void guardar(Long idEnvio, Trayecto t) {
        jdbc.update("INSERT INTO ruta_envio (id_envio, puntos, distancia_metros, duracion_segundos, por_carretera, "
                        + "fecha_calculo) VALUES (?, ?, ?, ?, ?, ?) ON DUPLICATE KEY UPDATE puntos = VALUES(puntos), "
                        + "distancia_metros = VALUES(distancia_metros), duracion_segundos = VALUES(duracion_segundos), "
                        + "por_carretera = VALUES(por_carretera), fecha_calculo = VALUES(fecha_calculo)",
                idEnvio, codificar(t.puntos()), t.distanciaMetros(), t.duracionSegundos(), t.porCarretera(),
                Sql.marca(t.fechaCalculo()));
    }

    @Override
    public Optional<Trayecto> buscar(Long idEnvio) {
        return jdbc.query("SELECT puntos, distancia_metros, duracion_segundos, por_carretera, fecha_calculo "
                        + "FROM ruta_envio WHERE id_envio = ?",
                (rs, fila) -> new Trayecto(decodificar(rs.getString("puntos")), rs.getDouble("distancia_metros"),
                        rs.getLong("duracion_segundos"), rs.getBoolean("por_carretera"),
                        Sql.instante(rs.getTimestamp("fecha_calculo"))), idEnvio).stream().findFirst();
    }

    static String codificar(List<Coordenada> puntos) {
        StringBuilder texto = new StringBuilder(puntos.size() * 20);
        for (Coordenada c : puntos) {
            if (texto.length() > 0) {
                texto.append(';');
            }
            texto.append(String.format(Locale.ROOT, "%.6f,%.6f", c.latitud(), c.longitud()));
        }
        return texto.toString();
    }

    static List<Coordenada> decodificar(String texto) {
        List<Coordenada> puntos = new ArrayList<>();
        for (String par : texto.split(";")) {
            String[] partes = par.split(",");
            puntos.add(new Coordenada(Double.parseDouble(partes[0]), Double.parseDouble(partes[1])));
        }
        return puntos;
    }
}
