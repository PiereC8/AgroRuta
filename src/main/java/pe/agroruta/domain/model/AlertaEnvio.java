package pe.agroruta.domain.model;

import java.time.Instant;

public record AlertaEnvio(Long idAlerta, Long idEnvio, TipoAlerta tipoAlerta, String descripcionAlerta,
                          Instant fechaAlerta) {

    public static AlertaEnvio nueva(Long idEnvio, TipoAlerta tipoAlerta, String descripcionAlerta, Instant ahora) {
        return new AlertaEnvio(null, idEnvio, tipoAlerta, descripcionAlerta, ahora);
    }

    public AlertaEnvio conId(Long nuevoIdAlerta) {
        return new AlertaEnvio(nuevoIdAlerta, idEnvio, tipoAlerta, descripcionAlerta, fechaAlerta);
    }
}
