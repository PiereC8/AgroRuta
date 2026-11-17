package pe.agroruta.infrastructure.adapter.out.seguridad;

import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.Optional;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.port.out.CifradoPort;

/**
 * RFF-09: cifra el token de los enlaces para volver a mostrarlos. AES-256-GCM con un vector al azar por mensaje.
 * Cómo: la clave se deriva del secreto de JWT (variable de entorno, no está en la base), así que con solo la base no
 * se pueden leer los enlaces. Si el secreto cambia, los enlaces guardados ya no se descifran y se generan de nuevo.
 */
@Component
public class CifradoAesAdapter implements CifradoPort {

    private static final int BYTES_VECTOR = 12;
    private static final int BITS_ETIQUETA = 128;

    private final SecretKeySpec clave;
    private final SecureRandom azar = new SecureRandom();

    public CifradoAesAdapter(@Value("${agroruta.jwt.secreto}") String secreto) {
        try {
            byte[] bytes = MessageDigest.getInstance("SHA-256")
                    .digest(("agroruta-enlaces|" + secreto).getBytes(StandardCharsets.UTF_8));
            this.clave = new SecretKeySpec(bytes, "AES");
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("SHA-256 no está disponible", e);
        }
    }

    @Override
    public String cifrar(String texto) {
        try {
            byte[] vector = new byte[BYTES_VECTOR];
            azar.nextBytes(vector);
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.ENCRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, vector));
            byte[] cifrado = aes.doFinal(texto.getBytes(StandardCharsets.UTF_8));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(
                    ByteBuffer.allocate(vector.length + cifrado.length).put(vector).put(cifrado).array());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No se pudo cifrar el enlace", e);
        }
    }

    @Override
    public Optional<String> descifrar(String cifrado) {
        try {
            byte[] todo = Base64.getUrlDecoder().decode(cifrado);
            if (todo.length <= BYTES_VECTOR) {
                return Optional.empty();
            }
            Cipher aes = Cipher.getInstance("AES/GCM/NoPadding");
            aes.init(Cipher.DECRYPT_MODE, clave, new GCMParameterSpec(BITS_ETIQUETA, todo, 0, BYTES_VECTOR));
            byte[] texto = aes.doFinal(todo, BYTES_VECTOR, todo.length - BYTES_VECTOR);
            return Optional.of(new String(texto, StandardCharsets.UTF_8));
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return Optional.empty();
        }
    }
}
