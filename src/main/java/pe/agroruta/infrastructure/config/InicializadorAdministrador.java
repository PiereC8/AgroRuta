package pe.agroruta.infrastructure.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import pe.agroruta.domain.model.PoliticaClave;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.out.HashClavePort;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;

@Component
public class InicializadorAdministrador implements ApplicationRunner {

    private static final Logger REGISTRO = LoggerFactory.getLogger(InicializadorAdministrador.class);

    private final UsuarioRepositoryPort usuarios;
    private final HashClavePort hashes;
    private final String nombreUsuario;
    private final String clave;

    public InicializadorAdministrador(UsuarioRepositoryPort usuarios, HashClavePort hashes,
                                      @Value("${agroruta.administrador.usuario}") String nombreUsuario,
                                      @Value("${agroruta.administrador.clave}") String clave) {
        this.usuarios = usuarios;
        this.hashes = hashes;
        this.nombreUsuario = nombreUsuario;
        this.clave = clave;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (nombreUsuario.isBlank() || clave.isBlank()) {
            REGISTRO.warn("No hay administrador de plataforma configurado (ADMIN_PLATAFORMA_USUARIO y ADMIN_PLATAFORMA_CLAVE)");
            return;
        }
        if (usuarios.existePorNombre(null, nombreUsuario)) {
            return;
        }
        PoliticaClave.validar(clave);
        usuarios.guardar(null, Usuario.nuevo(nombreUsuario, hashes.cifrar(clave), Rol.ADMINISTRADOR_PLATAFORMA));
        REGISTRO.info("Administrador de plataforma creado: {}", nombreUsuario);
    }
}
