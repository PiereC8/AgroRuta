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
            if (respuesta.requiereCodigo) {
                pedirCodigo(respuesta);
                return;
            }
            entrarConSesion(respuesta);
        } catch (error) {
            // Nunca se dice cuál de los datos falló: así no se ayuda a adivinar usuarios.
            mostrarAviso(error.estado === 401
                ? 'El negocio, el usuario o la contraseña no son correctos. Después de 5 intentos fallidos la cuenta '
                    + 'se bloquea 15 minutos.'
                : error.message);
        } finally {
            ocupado(false);
        }
    }

    function entrarConSesion(respuesta) {
        window.Api.guardarSesion(respuesta.token, {
            nombreUsuario: $('usuario').value.trim(),
            rolUsuario: respuesta.rolUsuario,
            identificadorNegocio: respuesta.identificadorNegocio
        });
        // Dueño sin correo: entra, y en Inicio se le pide registrarlo para activar la verificación en dos pasos.
        try {
            if (respuesta.avisoCorreo) {
                sessionStorage.setItem('agroruta.avisoCorreo', '1');
            } else {
                sessionStorage.removeItem('agroruta.avisoCorreo');
            }
        } catch (e) {
            // Sin almacenamiento solo se pierde el aviso.
        }
        window.location.replace('/inicio.html');
    }

    // ---------- Verificación en dos pasos (RFF-01, solo el Dueño) ----------
    // El servidor no da el token hasta recibir el código de 6 dígitos que le llegó al Dueño a su correo.

    let desafio = null;
    let negocioDelCodigo = null;
    let temporizador = null;

    function errorCodigo(mensaje) {
        $('campo-codigo').classList.toggle('campo--error', Boolean(mensaje));
        $('codigo').setAttribute('aria-invalid', mensaje ? 'true' : 'false');
        $('codigo-error').textContent = mensaje || '';
        $('codigo-error').classList.toggle('oculto', !mensaje);
    }

    // «Enviar otro código» se habilita a los 60 segundos, como pide el servidor.
    function esperarParaReenviar(segundos) {
        clearInterval(temporizador);
        let quedan = segundos;
        const boton = $('reenviar');
        const pintar = () => {
            boton.disabled = quedan > 0;
            boton.textContent = quedan > 0 ? 'Enviar otro código (' + quedan + ' s)' : 'Enviar otro código';
        };
        pintar();
        temporizador = setInterval(() => {
            quedan -= 1;
            pintar();
            if (quedan <= 0) {
                clearInterval(temporizador);
            }
        }, 1000);
    }

    function mostrarPaso(codigo) {
        $('formulario').classList.toggle('oculto', codigo);
        $('enlace-recuperar').classList.toggle('oculto', codigo);
        $('formulario-codigo').classList.toggle('oculto', !codigo);
    }

    function pedirCodigo(respuesta) {
        desafio = respuesta.desafio;
        negocioDelCodigo = respuesta.identificadorNegocio;
        $('codigo-texto').textContent = 'Por tu seguridad, te enviamos un código de 6 dígitos a '
            + respuesta.correoOculto + '. Escríbelo para entrar.';
        $('codigo').value = '';
        errorCodigo(null);
        mostrarPaso(true);
        esperarParaReenviar(60);
        $('codigo').focus();
    }

    async function verificar(evento) {
        evento.preventDefault();
        ocultarAviso();
        const codigo = $('codigo').value.replace(/\D/g, '');
        if (codigo.length !== 6) {
            errorCodigo('Escribe los 6 dígitos del código.');
            $('codigo').focus();
            return;
        }
        errorCodigo(null);
        const boton = $('verificar');
        boton.disabled = true;
        boton.textContent = 'Verificando…';
        try {
            const respuesta = await window.Api.pedir('/api/auth/verificar', {
                metodo: 'POST',
                sinRedireccion: true,
                cuerpo: {identificadorNegocio: negocioDelCodigo, desafio, codigo}
            });
            clearInterval(temporizador);
            entrarConSesion(respuesta);
        } catch (error) {
            errorCodigo(error.message);
            $('codigo').select();
        } finally {
            boton.disabled = false;
            boton.textContent = 'Verificar y entrar';
        }
    }

    async function reenviar() {
        ocultarAviso();
        $('reenviar').disabled = true;
        try {
            const respuesta = await window.Api.pedir('/api/auth/reenviar-codigo', {
                metodo: 'POST',
                sinRedireccion: true,
                cuerpo: {identificadorNegocio: negocioDelCodigo, desafio}
            });
            errorCodigo(null);
            $('codigo-texto').textContent = respuesta.mensaje + ' Escríbelo para entrar.';
            $('codigo').value = '';
            $('codigo').focus();
            esperarParaReenviar(60);
        } catch (error) {
            errorCodigo(error.message);
            $('reenviar').disabled = false;
        }
    }

    function volver() {
        clearInterval(temporizador);
        desafio = null;
        $('clave').value = '';
        mostrarPaso(false);
        $('clave').focus();
    }

    $('formulario-codigo').addEventListener('submit', verificar);
    $('reenviar').addEventListener('click', reenviar);
    $('volver').addEventListener('click', volver);
    // Si se pega el código del correo, se envía solo al tener los 6 dígitos.
    $('codigo').addEventListener('input', () => {
        if ($('codigo').value.replace(/\D/g, '').length === 6 && !$('verificar').disabled) {
            $('formulario-codigo').requestSubmit();
        }
    });

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
