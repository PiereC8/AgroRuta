package pe.agroruta.infrastructure.adapter.out.cache;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** Guarda valores por negocio durante un tiempo. Cada negocio tiene su propia memoria y se puede borrar entera. */
public class MemoriaConExpiracion {

    private record Valor(Object contenido, Instant venceEn) {
    }

    private final Duration duracion;
    private final Clock reloj;
    private final Map<String, Map<String, Valor>> porNegocio = new ConcurrentHashMap<>();

    public MemoriaConExpiracion(Duration duracion, Clock reloj) {
        this.duracion = duracion;
        this.reloj = reloj;
    }

    @SuppressWarnings("unchecked")
    public <T> Optional<T> buscar(String negocio, String clave) {
        Map<String, Valor> memoria = porNegocio.get(negocio);
        Valor valor = memoria == null ? null : memoria.get(clave);
        if (valor == null) {
            return Optional.empty();
        }
        if (!reloj.instant().isBefore(valor.venceEn())) {
            memoria.remove(clave, valor);
            return Optional.empty();
        }
        return Optional.of((T) valor.contenido());
    }

    public void guardar(String negocio, String clave, Object contenido) {
        porNegocio.computeIfAbsent(negocio, n -> new ConcurrentHashMap<>())
                .put(clave, new Valor(contenido, reloj.instant().plus(duracion)));
    }

    public void borrarNegocio(String negocio) {
        porNegocio.remove(negocio);
    }
}
