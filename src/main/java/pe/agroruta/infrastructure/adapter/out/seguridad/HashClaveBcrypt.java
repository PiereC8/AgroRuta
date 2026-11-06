package pe.agroruta.infrastructure.adapter.out.seguridad;

import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.port.out.HashClavePort;

@Component
public class HashClaveBcrypt implements HashClavePort {

    private final BCryptPasswordEncoder codificador = new BCryptPasswordEncoder();

    @Override
    public String cifrar(String clave) {
        return codificador.encode(clave);
    }

    @Override
    public boolean coincide(String clave, String hashClave) {
        return codificador.matches(clave, hashClave);
    }
}
