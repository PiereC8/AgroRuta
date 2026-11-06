package pe.agroruta.infrastructure.adapter.out.seguridad;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import java.util.Optional;

import javax.crypto.SecretKey;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.port.out.EmisorTokenPort;

public class ServicioJwt implements EmisorTokenPort {

    public record DatosToken(String nombreUsuario, String identificadorNegocio, Rol rolUsuario) {
    }

    private static final String CLAVE_NEGOCIO = "negocio";
    private static final String CLAVE_ROL = "rol";

    private final SecretKey llave;
    private final Duration validez;
    private final Clock reloj;

    public ServicioJwt(String secreto, Duration validez, Clock reloj) {
        byte[] bytes = secreto == null ? new byte[0] : secreto.getBytes(StandardCharsets.UTF_8);
        if (bytes.length < 32) {
            throw new IllegalStateException("JWT_SECRETO debe tener al menos 32 caracteres");
        }
        this.llave = Keys.hmacShaKeyFor(bytes);
        this.validez = validez;
        this.reloj = reloj;
    }

    @Override
    public String emitir(String identificadorNegocio, String nombreUsuario, Rol rolUsuario) {
        Instant ahora = reloj.instant();
        var constructor = Jwts.builder()
                .subject(nombreUsuario)
                .claim(CLAVE_ROL, rolUsuario.name())
                .issuedAt(Date.from(ahora))
                .expiration(Date.from(ahora.plus(validez)));
        if (identificadorNegocio != null) {
            constructor.claim(CLAVE_NEGOCIO, identificadorNegocio);
        }
        return constructor.signWith(llave).compact();
    }

    public Optional<DatosToken> leer(String token) {
        try {
            Claims datos = Jwts.parser()
                    .verifyWith(llave)
                    .clock(() -> Date.from(reloj.instant()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();
            return Optional.of(new DatosToken(
                    datos.getSubject(),
                    datos.get(CLAVE_NEGOCIO, String.class),
                    Rol.valueOf(datos.get(CLAVE_ROL, String.class))));
        } catch (JwtException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
