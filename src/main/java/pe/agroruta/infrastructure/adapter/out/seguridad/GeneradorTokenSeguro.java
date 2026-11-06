package pe.agroruta.infrastructure.adapter.out.seguridad;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

import org.springframework.stereotype.Component;

import pe.agroruta.domain.port.out.GeneradorTokenPort;

@Component
public class GeneradorTokenSeguro implements GeneradorTokenPort {

    private static final int BYTES = 32;

    private final SecureRandom azar = new SecureRandom();

    @Override
    public String nuevoToken() {
        byte[] bytes = new byte[BYTES];
        azar.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    @Override
    public String hash(String token) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }
}
