'use strict';

/**
 * Sin token: pide el enlace por correo. Con token (llega en el enlace, después de "#"): pone la contraseña nueva.
 * El token va en el fragmento para que no viaje al servidor de páginas ni quede en registros o en el Referer.
 */
(function () {
    const $ = (id) => document.getElementById(id);

    const negocio = new URLSearchParams(window.location.search).get('negocio') || '';
    const token = new URLSearchParams(window.location.hash.slice(1)).get('token') || '';
    if (token) {
        // Se saca de la barra de direcciones y del historial apenas se lee.
        history.replaceState(null, '', window.location.pathname + window.location.search);
    }

    function marcarError(id, mensaje) {
        $('campo-' + id).classList.toggle('campo--error', Boolean(mensaje));
        $(id).setAttribute('aria-invalid', mensaje ? 'true' : 'false');
        $(id + '-error').textContent = mensaje || '';
        $(id + '-error').classList.toggle('oculto', !mensaje);
        return !mensaje;
    }

    function mostrarAviso(texto, error) {
        const aviso = $('aviso');
        aviso.textContent = texto;
        aviso.classList.toggle('aviso--error', Boolean(error));
        aviso.classList.remove('oculto');
        aviso.focus();
    }

    function ocupado(boton, si, texto) {
        boton.disabled = si;
        if (texto) {
            boton.textContent = texto;
        }
    }

    async function pedirEnlace(evento) {
        evento.preventDefault();
        const datos = {identificadorNegocio: $('negocio').value.trim(), nombreUsuario: $('usuario').value.trim()};
        const bien = [marcarError('negocio', datos.identificadorNegocio ? null : 'Escribe el código de tu negocio.'),
            marcarError('usuario', datos.nombreUsuario ? null : 'Escribe tu usuario.')];
        if (bien.includes(false)) {
            $(bien[0] ? 'usuario' : 'negocio').focus();
            return;
        }
        const boton = $('enviar-pedido');
        ocupado(boton, true, 'Enviando…');
        try {
            const respuesta = await window.Api.pedir('/api/auth/recuperar', {
                metodo: 'POST', sinRedireccion: true, cuerpo: datos
            });
            mostrarAviso(respuesta.mensaje, false);
            $('pedir').classList.add('oculto');
        } catch (error) {
            mostrarAviso(error.message, true);
        } finally {
            ocupado(boton, false, 'Enviar enlace');
        }
    }

    async function cambiarClave(evento) {
        evento.preventDefault();
        const clave = $('clave').value;
        const bien = [marcarError('clave', clave.length < 8 || clave.length > 72
            ? 'Usa de 8 a 72 caracteres.' : null),
        marcarError('repetir', $('repetir').value !== clave ? 'No coincide con la contraseña nueva.' : null)];
        if (bien.includes(false)) {
            $(bien[0] ? 'repetir' : 'clave').focus();
            return;
        }
        const boton = $('enviar-cambio');
        ocupado(boton, true, 'Guardando…');
        try {
            await window.Api.pedir('/api/auth/restablecer', {
                metodo: 'POST', sinRedireccion: true,
                cuerpo: {identificadorNegocio: negocio, token: token, claveNueva: clave}
            });
            $('cambiar').classList.add('oculto');
            mostrarAviso('Listo: tu contraseña cambió. Ya puedes entrar con ella.', false);
        } catch (error) {
            mostrarAviso(error.message, true);
        } finally {
            ocupado(boton, false, 'Guardar contraseña');
        }
    }

    if (token && negocio) {
        $('pedir').classList.add('oculto');
        $('cambiar').classList.remove('oculto');
        $('titulo').textContent = 'Contraseña nueva';
        $('explicacion').textContent = 'Negocio ' + negocio + '. Elige una contraseña que no uses en otro sitio.';
        $('clave').focus();
    } else {
        $('negocio').value = negocio;
        $(negocio ? 'usuario' : 'negocio').focus();
    }
    $('pedir').addEventListener('submit', pedirEnlace);
    $('cambiar').addEventListener('submit', cambiarClave);
})();
