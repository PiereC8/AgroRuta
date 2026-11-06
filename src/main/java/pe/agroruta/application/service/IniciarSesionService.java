package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Instant;

import pe.agroruta.domain.model.CredencialesInvalidasException;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.in.IniciarSesionUseCase;
import pe.agroruta.domain.port.out.EmisorTokenPort;
import pe.agroruta.domain.port.out.HashClavePort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;

public class IniciarSesionService implements IniciarSesionUseCase {

    private final UsuarioRepositoryPort usuarios;
    private final NegocioRepositoryPort negocios;
    private final HashClavePort hashes;
    private final EmisorTokenPort emisor;
    private final Clock reloj;

    public IniciarSesionService(UsuarioRepositoryPort usuarios, NegocioRepositoryPort negocios,
                                HashClavePort hashes, EmisorTokenPort emisor, Clock reloj) {
        this.usuarios = usuarios;
        this.negocios = negocios;
        this.hashes = hashes;
        this.emisor = emisor;
        this.reloj = reloj;
    }

    @Override
    public Resultado iniciar(Comando comando) {
        String identificadorNegocio = comando.identificadorNegocio();
        if (identificadorNegocio != null && negocios.buscarActivoPorIdentificador(identificadorNegocio).isEmpty()) {
            throw new CredencialesInvalidasException();
        }

        Usuario usuario = usuarios.buscarPorNombre(identificadorNegocio, comando.nombreUsuario())
                .orElseThrow(CredencialesInvalidasException::new);

        Instant ahora = reloj.instant();
        if (!usuario.usuarioActivo() || usuario.estaBloqueado(ahora)) {
            throw new CredencialesInvalidasException();
        }
        if (comando.clave() == null || !hashes.coincide(comando.clave(), usuario.hashClave())) {
            usuarios.guardar(identificadorNegocio, usuario.conIntentoFallido(ahora));
            throw new CredencialesInvalidasException();
        }

        if (usuario.intentosFallidosSesion() > 0 || usuario.fechaBloqueoHasta() != null) {
            usuarios.guardar(identificadorNegocio, usuario.conAccesoExitoso());
        }
        String token = emisor.emitir(identificadorNegocio, usuario.nombreUsuario(), usuario.rolUsuario());
        return new Resultado(token, usuario.rolUsuario(), identificadorNegocio);
    }
}
