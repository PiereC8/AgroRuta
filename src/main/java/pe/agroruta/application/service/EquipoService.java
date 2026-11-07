package pe.agroruta.application.service;

import java.util.List;

import pe.agroruta.domain.model.ConflictoDominioException;
import pe.agroruta.domain.model.Correo;
import pe.agroruta.domain.model.DominioException;
import pe.agroruta.domain.model.MiembroEquipo;
import pe.agroruta.domain.model.PoliticaClave;
import pe.agroruta.domain.model.RecursoNoEncontradoException;
import pe.agroruta.domain.model.Rol;
import pe.agroruta.domain.model.Usuario;
import pe.agroruta.domain.port.in.AdministrarEquipoUseCase;
import pe.agroruta.domain.port.out.EquipoRepositoryPort;
import pe.agroruta.domain.port.out.HashClavePort;

public class EquipoService implements AdministrarEquipoUseCase {

    private final EquipoRepositoryPort equipo;
    private final HashClavePort hashes;

    public EquipoService(EquipoRepositoryPort equipo, HashClavePort hashes) {
        this.equipo = equipo;
        this.hashes = hashes;
    }

    @Override
    public List<MiembroEquipo> listar() {
        return equipo.listar();
    }

    @Override
    public MiembroEquipo crearOperador(String nombreUsuario, String clave, String correo) {
        PoliticaClave.validar(clave);
        String correoLimpio = Correo.normalizar(correo);
        String hash = hashes.cifrar(clave);
        Usuario operador = Usuario.nuevo(nombreUsuario, hash, Rol.OPERADOR);
        if (equipo.existePorNombre(operador.nombreUsuario())) {
            throw new ConflictoDominioException("Ya existe un usuario con el nombre " + operador.nombreUsuario());
        }
        return equipo.crearOperador(operador.nombreUsuario(), hash, correoLimpio);
    }

    @Override
    public void cambiarCorreo(Long idUsuario, String correo, boolean avisosActivos) {
        MiembroEquipo miembro = existente(idUsuario);
        String correoLimpio = Correo.normalizar(correo);
        if (avisosActivos && correoLimpio == null) {
            throw new DominioException("Para recibir avisos hace falta escribir un correo");
        }
        equipo.actualizarCorreo(miembro.idUsuario(), correoLimpio, avisosActivos);
    }

    @Override
    public void cambiarActivo(Long idUsuario, boolean activo, String solicitante) {
        MiembroEquipo miembro = existente(idUsuario);
        if (!activo && miembro.nombreUsuario().equals(solicitante)) {
            throw new DominioException("No puedes desactivar a ti mismo");
        }
        equipo.actualizarActivo(miembro.idUsuario(), activo);
    }

    private MiembroEquipo existente(Long idUsuario) {
        return equipo.buscar(idUsuario)
                .orElseThrow(() -> new RecursoNoEncontradoException("No existe esa persona en el equipo"));
    }
}
