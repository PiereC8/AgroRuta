'use strict';

(function () {
    const $ = (id) => document.getElementById(id);

    const CAMPOS = [
        {id: 'negocio', vacio: 'Escribe el código de tu negocio.'},
        {id: 'usuario', vacio: 'Escribe tu usuario.'},
        {id: 'clave', vacio: 'Escribe tu contraseña.'}
    ];

    if (window.Api.haySesion()) {
        window.location.replace('/inicio.html');
        return;
    }

    function marcarError(campo, mensaje) {
        const caja = $('campo-' + campo.id);
        const entrada = $(campo.id);
        const texto = $(campo.id + '-error');
        caja.classList.toggle('campo--error', Boolean(mensaje));
        entrada.setAttribute('aria-invalid', mensaje ? 'true' : 'false');
        texto.textContent = mensaje || '';
        texto.classList.toggle('oculto', !mensaje);
    }

    /** Revisa un campo y muestra al instante qué falta. Devuelve verdadero si está bien. */
    function revisar(campo) {
        const valor = $(campo.id).value.trim();
        marcarError(campo, valor ? null : campo.vacio);
        return Boolean(valor);
    }

    function mostrarAviso(texto) {
        const aviso = $('aviso');
        aviso.textContent = texto;
        aviso.classList.remove('oculto');
        aviso.focus();
    }

    function ocultarAviso() {
        $('aviso').classList.add('oculto');
    }

    function ocupado(si) {
        const boton = $('entrar');
        boton.disabled = si;
        boton.textContent = si ? 'Entrando…' : 'Entrar';
    }

    async function entrar(evento) {
        evento.preventDefault();
        ocultarAviso();
        const fallos = CAMPOS.filter((campo) => !revisar(campo));
        if (fallos.length > 0) {
            $(fallos[0].id).focus();
            return;
        }
        ocupado(true);
        try {
            const respuesta = await window.Api.pedir('/api/auth/login', {
                metodo: 'POST',
                sinRedireccion: true,
                cuerpo: {
                    identificadorNegocio: $('negocio').value.trim(),
                    nombreUsuario: $('usuario').value.trim(),
                    clave: $('clave').value
                }
            });
            if (respuesta.rolUsuario !== 'DUENO' && respuesta.rolUsuario !== 'OPERADOR') {
                mostrarAviso('Esta pantalla es para el Dueño y el Operador de un negocio.');
                return;
            }
            window.Api.guardarSesion(respuesta.token, {
                nombreUsuario: $('usuario').value.trim(),
                rolUsuario: respuesta.rolUsuario,
                identificadorNegocio: respuesta.identificadorNegocio
            });
            window.location.replace('/inicio.html');
        } catch (error) {
            // Nunca se dice cuál de los datos falló: así no se ayuda a adivinar usuarios.
            mostrarAviso(error.estado === 401
                ? 'El negocio, el usuario o la contraseña no son correctos. Revísalos e inténtalo otra vez.'
                : error.message);
        } finally {
            ocupado(false);
        }
    }

    for (const campo of CAMPOS) {
        $(campo.id).addEventListener('blur', () => {
            if ($(campo.id).value.trim() || $('campo-' + campo.id).classList.contains('campo--error')) {
                revisar(campo);
            }
        });
        $(campo.id).addEventListener('input', () => {
            if ($('campo-' + campo.id).classList.contains('campo--error')) {
                revisar(campo);
            }
        });
    }

    $('ver-clave').addEventListener('click', () => {
        const visible = $('clave').type === 'text';
        $('clave').type = visible ? 'password' : 'text';
        $('ver-clave').textContent = visible ? 'Mostrar' : 'Ocultar';
        $('ver-clave').setAttribute('aria-pressed', visible ? 'false' : 'true');
    });
    $('formulario').addEventListener('submit', entrar);
})();
