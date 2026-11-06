package pe.agroruta.application.service;

import pe.agroruta.domain.model.Negocio;
import pe.agroruta.domain.model.NegocioYaExisteException;
import pe.agroruta.domain.model.PoliticaClave;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.in.AltaNegocioUseCase;
import pe.agroruta.domain.port.out.BaseNegocioPort;
import pe.agroruta.domain.port.out.HashClavePort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;

public class AltaNegocioService implements AltaNegocioUseCase {

    private final NegocioRepositoryPort negocios;
    private final BaseNegocioPort bases;
    private final UsuarioRepositoryPort usuarios;
    private final HashClavePort hashes;

    public AltaNegocioService(NegocioRepositoryPort negocios, BaseNegocioPort bases,
                              UsuarioRepositoryPort usuarios, HashClavePort hashes) {
        this.negocios = negocios;
        this.bases = bases;
        this.usuarios = usuarios;
        this.hashes = hashes;
    }

    @Override
    public Negocio darDeAlta(Comando comando) {
        Negocio negocio = Negocio.nuevo(comando.identificadorNegocio(), comando.nombreNegocio(),
                comando.planSuscripcion());
        PoliticaClave.validar(comando.claveInicialDueno());
        Usuario dueno = Usuario.nuevo(comando.nombreUsuarioDueno(),
                hashes.cifrar(comando.claveInicialDueno()), Rol.DUENO);

        if (negocios.existePorIdentificador(negocio.identificadorNegocio())) {
            throw new NegocioYaExisteException(negocio.identificadorNegocio());
        }
        bases.crearYMigrar(negocio.identificadorNegocio());
        if (!usuarios.existePorNombre(negocio.identificadorNegocio(), dueno.nombreUsuario())) {
            usuarios.guardar(negocio.identificadorNegocio(), dueno);
        }
        return negocios.guardar(negocio);
    }
}
