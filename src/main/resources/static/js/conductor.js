'use strict';

(function () {
    const INTERVALO_MS = 15000;
    const TIEMPO_ACUSE_MS = 10000;
    const ESPERA_MAXIMA_RECONEXION_MS = 10000;

    const $ = (id) => document.getElementById(id);
    const token = new URLSearchParams(location.hash.slice(1)).get('t');

    const estado = {
        compartiendo: false,
        gpsActivo: true,
        gpsAvisado: true,
        enviando: false,
        cliente: null,
        temporizador: null,
        reconexion: null,
        intentos: 0,
        acuses: new Map(),
        bloqueoPantalla: null,
        ultimaEnviada: null
    };

    const cola = (function () {
        const memoria = new Map();
        let base = null;

        function abrir() {
            return new Promise((ok, mal) => {
                const peticion = indexedDB.open('agroruta-conductor', 1);
                peticion.onupgradeneeded = () => peticion.result.createObjectStore('cola', {keyPath: 'idLocal'});
                peticion.onsuccess = () => ok(peticion.result);
                peticion.onerror = () => mal(peticion.error);
            });
        }

        async function obtenerBase() {
            if (base === null) {
                try {
                    base = await abrir();
                } catch (e) {
                    base = false; // Sin IndexedDB (modo privado): se guarda solo en memoria.
                }
            }
            return base;
        }

        function transaccion(bd, modo, accion) {
            return new Promise((ok, mal) => {
                const t = bd.transaction('cola', modo);
                const peticion = accion(t.objectStore('cola'));
                t.oncomplete = () => ok(peticion.result);
                t.onerror = () => mal(t.error);
                t.onabort = () => mal(t.error);
            });
        }

        return {
            async agregar(posicion) {
                const bd = await obtenerBase();
                if (!bd) {
                    memoria.set(posicion.idLocal, posicion);
                    return;
                }
                await transaccion(bd, 'readwrite', (s) => s.put(posicion));
            },
            async todas() {
                const bd = await obtenerBase();
                const lista = bd ? await transaccion(bd, 'readonly', (s) => s.getAll()) : [...memoria.values()];
                return lista.sort((a, b) => a.fechaMs - b.fechaMs);
            },
            async borrar(idLocal) {
                const bd = await obtenerBase();
                if (!bd) {
                    memoria.delete(idLocal);
                    return;
                }
                await transaccion(bd, 'readwrite', (s) => s.delete(idLocal));
            }
        };
    })();

    function mostrar(idSeccion, visible) {
        $(idSeccion).classList.toggle('oculto', !visible);
    }

    function aviso(texto, clase) {
        const p = document.createElement('p');
        p.className = 'mensaje ' + (clase || 'aviso');
        p.textContent = texto;
        $('mensajes').replaceChildren(p);
    }

    function limpiarAviso() {
        $('mensajes').replaceChildren();
    }

    function estadoPrincipal(texto, clase) {
        const caja = $('estado-principal');
        caja.textContent = texto;
        caja.className = 'mensaje ' + clase;
    }

    async function actualizarPendientes() {
        $('dato-pendientes').textContent = String((await cola.todas()).length);
    }

    function actualizarConexion(conectado) {
        $('dato-conexion').textContent = conectado ? 'Conectado' : 'Sin señal (guardando posiciones)';
        if (estado.compartiendo) {
            if (conectado && estado.gpsActivo) {
                estadoPrincipal('Compartiendo tu ubicación.', 'ok');
            } else if (!estado.gpsActivo) {
                estadoPrincipal('GPS desactivado: actívalo para seguir compartiendo.', 'error');
            } else {
                estadoPrincipal('Sin señal. Guardando tus posiciones para enviarlas luego.', 'aviso');
            }
        }
    }

    function esperarAcuse(clave) {
        return new Promise((ok, mal) => {
            const reloj = setTimeout(() => {
                estado.acuses.delete(clave);
                mal(new Error('Sin respuesta del servidor'));
            }, TIEMPO_ACUSE_MS);
            estado.acuses.set(clave, (acuse) => {
                clearTimeout(reloj);
                estado.acuses.delete(clave);
                ok(acuse);
            });
        });
    }

    function alMensaje(destino, cuerpo) {
        if (destino === '/user/queue/acuses') {
            const clave = cuerpo.tipo === 'POSICION' ? cuerpo.idLocal : cuerpo.tipo;
            const resolver = estado.acuses.get(clave);
            if (resolver) {
                resolver(cuerpo);
            }
        } else if (destino === '/user/queue/errores') {
            aviso(cuerpo.detalle, 'aviso');
        }
    }

    async function conectar() {
        const cliente = new ClienteStomp(window.AgroRutaConfig.urlWebSocket, {token}, {
            alMensaje,
            alError: () => {
            },
            alCerrar: (estabaConectado) => {
                if (estado.cliente === cliente) {
                    estado.cliente = null;
                    actualizarConexion(false);
                    if (estado.compartiendo) {
                        programarReconexion();
                    }
                }
            }
        });
        await cliente.conectar();
        cliente.suscribir('/user/queue/acuses');
        cliente.suscribir('/user/queue/errores');
        estado.cliente = cliente;
        estado.intentos = 0;
        actualizarConexion(true);
        return cliente;
    }

    function programarReconexion(esperaForzadaMs) {
        if (estado.reconexion !== null || !estado.compartiendo) {
            return;
        }
        const espera = esperaForzadaMs !== undefined ? esperaForzadaMs
            : Math.min(2000 * Math.pow(2, estado.intentos), ESPERA_MAXIMA_RECONEXION_MS);
        estado.intentos += 1;
        estado.reconexion = setTimeout(async () => {
            estado.reconexion = null;
            try {
                await conectar();
                await avisarEstadoGpsPendiente();
                await vaciarCola();
            } catch (e) {
                if (e && /autorizado|inválido/i.test(e.message)) {
                    detener('El enlace ya no es válido. Pide uno nuevo al operador.');
                    return;
                }
                programarReconexion();
            }
        }, espera);
    }

    function obtenerUbicacion() {
        return new Promise((ok, mal) => {
            navigator.geolocation.getCurrentPosition(ok, mal,
                {enableHighAccuracy: true, timeout: 10000, maximumAge: 5000});
        });
    }

    async function vaciarCola() {
        if (estado.enviando || !estado.cliente) {
            return;
        }
        estado.enviando = true;
        try {
            for (const posicion of await cola.todas()) {
                if (!estado.cliente) {
                    break;
                }
                const espera = esperarAcuse(posicion.idLocal);
                estado.cliente.enviar('/app/posicion', posicion);
                let acuse;
                try {
                    acuse = await espera;
                } catch (e) {
                    // Sin respuesta: la conexión no sirve. Se cierra y se reintenta; la cola sigue intacta.
                    if (estado.cliente) {
                        estado.cliente.cerrar();
                    }
                    break;
                }
                await cola.borrar(posicion.idLocal);
                if (acuse.estado === 'RECHAZADA') {
                    aviso('Una posición fue rechazada: ' + acuse.detalle, 'aviso');
                    if (/no corresponde al tiempo|no está en ruta/i.test(acuse.detalle || '')) {
                        detener('El envío ya terminó. Gracias.');
                        return;
                    }
                } else {
                    estado.ultimaEnviada = posicion;
                    $('dato-ultima').textContent = new Date(posicion.fechaMs).toLocaleTimeString();
                    $('dato-precision').textContent = posicion.precisionMetros
                        ? Math.round(posicion.precisionMetros) + ' m' : '-';
                }
            }
        } finally {
            estado.enviando = false;
            await actualizarPendientes();
        }
    }

    async function avisarEstadoGpsPendiente() {
        if (estado.cliente && !estado.gpsAvisado) {
            estado.cliente.enviar('/app/estado-gps', {gpsActivo: estado.gpsActivo});
            estado.gpsAvisado = true;
        }
    }

    function marcarGps(activo) {
        if (estado.gpsActivo === activo) {
            return;
        }
        estado.gpsActivo = activo;
        estado.gpsAvisado = false;
        actualizarConexion(!!estado.cliente);
        avisarEstadoGpsPendiente().catch(() => {
        });
    }

    async function tomarPosicion() {
        if (!estado.compartiendo) {
            return;
        }
        try {
            const lectura = await obtenerUbicacion();
            if (!estado.gpsActivo) {
                limpiarAviso();
            }
            marcarGps(true);
            await cola.agregar({
                idLocal: crypto.randomUUID(),
                latitud: lectura.coords.latitude,
                longitud: lectura.coords.longitude,
                precisionMetros: lectura.coords.accuracy,
                fechaMs: lectura.timestamp
            });
            await actualizarPendientes();
            await vaciarCola();
        } catch (e) {
            if (e && (e.code === 1 || e.code === 2)) {
                marcarGps(false);
                aviso(e.code === 1
                    ? 'Se bloqueó el permiso de ubicación. Actívalo en los ajustes del navegador.'
                    : 'No se encuentra la ubicación. Activa el GPS del celular.', 'error');
            } else if (!e || e.code !== 3) {
                aviso('No se pudo guardar o enviar la posición. Se reintentará.', 'aviso');
            }
        }
    }

    async function mantenerPantallaEncendida() {
        try {
            if ('wakeLock' in navigator) {
                estado.bloqueoPantalla = await navigator.wakeLock.request('screen');
            }
        } catch (e) {
            // Si no se puede, el conductor debe dejar la pantalla encendida a mano.
        }
    }

    async function comenzar() {
        limpiarAviso();
        $('comenzar').disabled = true;
        if (!('geolocation' in navigator)) {
            aviso('Este navegador no permite obtener la ubicación. Usa Chrome o Safari actualizados.', 'error');
            $('comenzar').disabled = false;
            return;
        }
        try {
            await obtenerUbicacion();
        } catch (e) {
            aviso(e.code === 1
                ? 'Permiso de ubicación denegado. Actívalo en los ajustes del navegador y vuelve a intentar.'
                : 'No se pudo obtener tu ubicación. Activa el GPS del celular y vuelve a intentar.', 'error');
            $('comenzar').disabled = false;
            return;
        }
        try {
            const cliente = await conectar();
            const espera = esperarAcuse('INICIO');
            cliente.enviar('/app/iniciar', {gpsActivo: true, permisoUbicacion: true, consentimiento: true});
            const acuse = await espera;
            if (acuse.estado !== 'ACEPTADA') {
                cliente.cerrar();
                aviso(acuse.detalle || 'No se pudo iniciar.', 'error');
                $('comenzar').disabled = false;
                return;
            }
        } catch (e) {
            aviso(/autorizado|inválido/i.test(e.message)
                ? 'El enlace ya no es válido. Pide uno nuevo al operador.'
                : 'No se pudo conectar. Revisa tu señal e intenta de nuevo.', 'error');
            $('comenzar').disabled = false;
            return;
        }
        estado.compartiendo = true;
        mostrar('consentimiento', false);
        mostrar('compartiendo', true);
        await mantenerPantallaEncendida();
        await actualizarPendientes();
        await tomarPosicion();
        estado.temporizador = setInterval(tomarPosicion, INTERVALO_MS);
    }

    function detener(motivo) {
        estado.compartiendo = false;
        clearInterval(estado.temporizador);
        clearTimeout(estado.reconexion);
        estado.reconexion = null;
        if (estado.cliente) {
            estado.cliente.cerrar();
            estado.cliente = null;
        }
        if (estado.bloqueoPantalla) {
            estado.bloqueoPantalla.release().catch(() => {
            });
        }
        mostrar('compartiendo', false);
        mostrar('consentimiento', false);
        aviso(motivo || 'Dejaste de compartir tu ubicación.', 'ok');
    }

    function vigilarPermiso() {
        if (!navigator.permissions || !navigator.permissions.query) {
            return;
        }
        navigator.permissions.query({name: 'geolocation'}).then((permiso) => {
            permiso.onchange = () => {
                if (estado.compartiendo) {
                    marcarGps(permiso.state !== 'denied');
                }
            };
        }).catch(() => {
        });
    }

    function reconectarYa() {
        if (!estado.compartiendo || estado.cliente) {
            return;
        }
        clearTimeout(estado.reconexion);
        estado.reconexion = null;
        estado.intentos = 0;
        programarReconexion(0);
    }

    document.addEventListener('visibilitychange', () => {
        if (document.visibilityState === 'visible' && estado.compartiendo) {
            mantenerPantallaEncendida();
            reconectarYa();
        }
    });
    window.addEventListener('online', reconectarYa);

    if (!token) {
        mostrar('sin-enlace', true);
    } else {
        mostrar('consentimiento', true);
        $('comenzar').addEventListener('click', comenzar);
        $('detener').addEventListener('click', () => detener());
        vigilarPermiso();
    }
})();
