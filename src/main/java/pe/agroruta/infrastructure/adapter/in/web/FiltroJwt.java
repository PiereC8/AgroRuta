package pe.agroruta.infrastructure.adapter.in.web;

import java.io.IOException;
import java.util.List;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import pe.agroruta.infrastructure.adapter.out.seguridad.ServicioJwt;
import pe.agroruta.infrastructure.config.TenantContext;

/** El negocio de cada petición sale solo del token firmado, nunca de la URL ni de un encabezado. */
public class FiltroJwt extends OncePerRequestFilter {

    private static final String PREFIJO = "Bearer ";

    private final ServicioJwt servicioJwt;

    public FiltroJwt(ServicioJwt servicioJwt) {
        this.servicioJwt = servicioJwt;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        try {
            String encabezado = peticion.getHeader("Authorization");
            if (encabezado != null && encabezado.startsWith(PREFIJO)) {
                servicioJwt.leer(encabezado.substring(PREFIJO.length())).ifPresent(datos -> {
                    var autenticacion = new UsernamePasswordAuthenticationToken(datos, null,
                            List.of(new SimpleGrantedAuthority("ROLE_" + datos.rolUsuario().name())));
                    SecurityContextHolder.getContext().setAuthentication(autenticacion);
                    if (datos.identificadorNegocio() != null) {
                        TenantContext.fijar(datos.identificadorNegocio());
                    }
                });
            }
            cadena.doFilter(peticion, respuesta);
        } finally {
            TenantContext.limpiar();
        }
    }
}
