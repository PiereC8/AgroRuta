'use strict';

/**
 * Llamadas al servidor con la sesión del usuario. El token vive en sessionStorage: se borra al cerrar la pestaña.
 * Si el servidor dice que la sesión ya no vale (401), se vuelve a la pantalla de entrada.
 */
window.Api = (function () {
    const CLAVE_TOKEN = 'agroruta.token';
    const CLAVE_USUARIO = 'agroruta.usuario';

    function leer(clave) {
        try {
            return sessionStorage.getItem(clave);
        } catch (e) {
            return null;
        }
    }

    function guardar(clave, valor) {
        try {
            sessionStorage.setItem(clave, valor);
        } catch (e) {
            // Sin almacenamiento la sesión dura solo mientras no se recargue la página.
        }
    }

    function guardarSesion(token, usuario) {
        guardar(CLAVE_TOKEN, token);
        guardar(CLAVE_USUARIO, JSON.stringify(usuario));
    }

    function cerrarSesion() {
        try {
            sessionStorage.removeItem(CLAVE_TOKEN);
            sessionStorage.removeItem(CLAVE_USUARIO);
        } catch (e) {
            // Nada que borrar.
        }
        window.location.replace('/login.html');
    }

    function usuario() {
        try {
            return JSON.parse(leer(CLAVE_USUARIO));
        } catch (e) {
            return null;
        }
    }

    function haySesion() {
        return Boolean(leer(CLAVE_TOKEN));
    }

    /** Pide una ruta del servidor y devuelve el JSON. Los errores traen un mensaje listo para mostrar. */
    async function pedir(ruta, opciones) {
        const cabeceras = Object.assign({'Accept': 'application/json'}, opciones && opciones.cabeceras);
        const token = leer(CLAVE_TOKEN);
        if (token) {
            cabeceras['Authorization'] = 'Bearer ' + token;
        }
        if (opciones && opciones.cuerpo !== undefined) {
            cabeceras['Content-Type'] = 'application/json';
        }
        let respuesta;
        try {
            respuesta = await fetch(ruta, {
                method: (opciones && opciones.metodo) || 'GET',
                headers: cabeceras,
                body: opciones && opciones.cuerpo !== undefined ? JSON.stringify(opciones.cuerpo) : undefined,
                cache: 'no-store'
            });
        } catch (e) {
            throw new Error('No hay conexión con el servidor. Revisa tu internet e inténtalo de nuevo.');
        }
        if (respuesta.status === 401 && !(opciones && opciones.sinRedireccion)) {
            cerrarSesion();
            throw new Error('Tu sesión terminó. Entra otra vez.');
        }
        const datos = await respuesta.json().catch(() => null);
        if (!respuesta.ok) {
            const error = new Error((datos && datos.error) || 'No se pudo completar la acción. Inténtalo de nuevo.');
            error.estado = respuesta.status;
            throw error;
        }
        return datos;
    }

    return {pedir, guardarSesion, cerrarSesion, usuario, haySesion};
})();
