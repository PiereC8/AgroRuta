'use strict';

(function () {
    const SEGUNDOS_SIN_SENAL = 60;
    const REFRESCO_SIN_WEBSOCKET_MS = 30000;
    const ESPERA_MAXIMA_RECONEXION_MS = 10000;

    const $ = (id) => document.getElementById(id);
    const token = new URLSearchParams(location.hash.slice(1)).get('t');

    const ESTADOS = {
        PROGRAMADO: 'Preparando el envío',
        EN_RUTA: 'En camino',
        ENTREGADO: 'Entregado'
    };

    const vista = {
        mapa: null,
        marcadorVehiculo: null,
        marcadorDestino: null,
        trazo: null,
        puntos: [],
        datos: null,
        desfaseMs: 0,
        ultimaFecha: null,
        eta: null,
        etaRecibidaMs: null,
        cliente: null,
        intentos: 0
    };

    function mostrar(id, visible) {
        $(id).classList.toggle('oculto', !visible);
    }

    function enlaceInvalido() {
        mostrar('contenido', false);
        mostrar('enlace-invalido', true);
    }

    // ---------- Mapa ----------

    function crearMapa() {
        vista.mapa = L.map('mapa', {zoomControl: true}).setView([-12.05, -77.04], 6);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; Colaboradores de OpenStreetMap'
        }).addTo(vista.mapa);
        vista.trazo = L.polyline([], {color: '#2f7d32', weight: 4, opacity: 0.8}).addTo(vista.mapa);
        // Si el contenedor cambia de tamaño (pestaña en segundo plano, girar el celular), el mapa se reajusta.
        if ('ResizeObserver' in window) {
            new ResizeObserver(() => vista.mapa.invalidateSize()).observe($('mapa'));
        }
    }

    function iconoVehiculo() {
        return L.divIcon({className: '', html: '<div class="marcador-vehiculo"></div>', iconSize: [18, 18]});
    }

    function iconoDestino() {
        return L.divIcon({className: '', html: '<div class="marcador-destino"></div>', iconSize: [16, 16]});
    }

    function dibujarDestino(datos) {
        if (datos.latitudDestino == null || datos.longitudDestino == null || vista.marcadorDestino) {
            return;
        }
        vista.marcadorDestino = L.marker([datos.latitudDestino, datos.longitudDestino], {icon: iconoDestino()})
            .addTo(vista.mapa);
    }

    function agregarPosicion(latitud, longitud, fechaIso) {
        const punto = [latitud, longitud];
        vista.puntos.push(punto);
        vista.trazo.setLatLngs(vista.puntos);
        if (!vista.marcadorVehiculo) {
            vista.marcadorVehiculo = L.marker(punto, {icon: iconoVehiculo()}).addTo(vista.mapa);
        } else {
            vista.marcadorVehiculo.setLatLng(punto);
        }
        vista.ultimaFecha = Date.parse(fechaIso);
        encuadrar();
    }

    function encuadrar() {
        const puntos = vista.puntos.slice();
        if (vista.marcadorDestino) {
            puntos.push(vista.marcadorDestino.getLatLng());
        }
        if (puntos.length === 1) {
            vista.mapa.setView(puntos[0], 14);
        } else if (puntos.length > 1) {
            vista.mapa.fitBounds(L.latLngBounds(puntos), {padding: [40, 40], maxZoom: 16});
        }
    }

    // ---------- Texto ----------

    function ahoraServidor() {
        return Date.now() + vista.desfaseMs;
    }

    function formatoHora(ms) {
        return new Date(ms).toLocaleTimeString([], {hour: '2-digit', minute: '2-digit'});
    }

    function pintarTextos() {
        const datos = vista.datos;
        if (!datos) {
            return;
        }
        $('dato-estado').textContent = ESTADOS[datos.estadoEnvio] || datos.estadoEnvio;
        $('dato-destino').textContent = datos.ciudadDestino || '-';

        const resumen = $('resumen');
        const aviso = $('aviso-senal');
        aviso.classList.add('oculto');

        mostrar('enlace-pago', datos.estadoEnvio === 'ENTREGADO');
        $('ir-a-pagar').href = '/pago.html#t=' + encodeURIComponent(token);

        if (datos.estadoEnvio === 'ENTREGADO') {
            resumen.textContent = 'Tu pedido fue entregado.';
            resumen.className = 'mensaje ok';
            $('dato-eta').textContent = '-';
        } else if (datos.llegoAlDestino) {
            resumen.textContent = 'El vehículo llegó a tu destino.';
            resumen.className = 'mensaje ok';
            $('dato-eta').textContent = 'Ya llegó';
        } else if (datos.estadoEnvio === 'PROGRAMADO') {
            resumen.textContent = 'Tu pedido se está preparando. Aún no sale en ruta.';
            resumen.className = 'mensaje';
            $('dato-eta').textContent = '-';
        } else {
            resumen.textContent = vista.ultimaFecha ? 'Tu pedido está en camino.' : 'Tu pedido salió en ruta. Esperando la ubicación del vehículo.';
            resumen.className = 'mensaje ok';
            if (vista.eta != null && vista.etaRecibidaMs != null) {
                const llegada = vista.etaRecibidaMs + vista.eta * 60000;
                $('dato-eta').textContent = 'en unos ' + vista.eta + ' min (' + formatoHora(llegada) + ')';
            } else {
                $('dato-eta').textContent = 'Aún no se puede calcular';
            }
        }

        if (vista.ultimaFecha) {
            const segundos = Math.max(0, Math.round((ahoraServidor() - vista.ultimaFecha) / 1000));
            $('dato-actualizacion').textContent = segundos < 90 ? 'hace ' + segundos + ' s'
                : 'hace ' + Math.round(segundos / 60) + ' min';
            if (datos.estadoEnvio === 'EN_RUTA' && segundos > SEGUNDOS_SIN_SENAL && !datos.llegoAlDestino) {
                aviso.textContent = 'Sin señal del vehículo desde hace ' + Math.round(segundos / 60)
                    + ' min. Mostramos su última posición conocida.';
                aviso.classList.remove('oculto');
            }
        } else {
            $('dato-actualizacion').textContent = '-';
        }
    }

    // ---------- Datos ----------

    function aplicarDatos(datos) {
        vista.datos = datos;
        vista.desfaseMs = Date.parse(datos.ahora) - Date.now();
        vista.eta = datos.etaMinutos;
        vista.etaRecibidaMs = ahoraServidor();
        dibujarDestino(datos);
        vista.puntos = [];
        for (const p of datos.trazoReciente) {
            agregarPosicion(p.latitud, p.longitud, p.fechaPosicion);
        }
        if (vista.puntos.length === 0) {
            encuadrar();
        }
        pintarTextos();
    }

    async function cargar() {
        const respuesta = await fetch('/api/publico/seguimiento', {
            headers: {'X-Token-Seguimiento': token},
            cache: 'no-store'
        });
        if (respuesta.status === 404 || respuesta.status === 401) {
            enlaceInvalido();
            return false;
        }
        if (!respuesta.ok) {
            throw new Error('Error ' + respuesta.status);
        }
        aplicarDatos(await respuesta.json());
        return true;
    }

    function alMensaje(destino, cuerpo) {
        if (!vista.datos) {
            return;
        }
        if (cuerpo.tipo === 'POSICION') {
            vista.eta = cuerpo.etaMinutos;
            vista.etaRecibidaMs = ahoraServidor();
            agregarPosicion(cuerpo.latitud, cuerpo.longitud, cuerpo.fecha);
        } else if (cuerpo.tipo === 'ALERTA' && cuerpo.tipoAlerta === 'LLEGADA') {
            vista.datos.llegoAlDestino = true;
        }
        pintarTextos();
    }

    // ---------- Tiempo real ----------

    async function conectarTiempoReal() {
        const protocolo = location.protocol === 'https:' ? 'wss://' : 'ws://';
        const cliente = new ClienteStomp(protocolo + location.host + '/ws', {token}, {
            alMensaje,
            alError: () => {
            },
            alCerrar: () => {
                if (vista.cliente === cliente) {
                    vista.cliente = null;
                    reconectar();
                }
            }
        });
        await cliente.conectar();
        cliente.suscribir(vista.datos.canal);
        vista.cliente = cliente;
        vista.intentos = 0;
        // Por si llegó algo mientras se reconectaba.
        cargar().catch(() => {
        });
    }

    function reconectar() {
        const espera = Math.min(2000 * Math.pow(2, vista.intentos), ESPERA_MAXIMA_RECONEXION_MS);
        vista.intentos += 1;
        setTimeout(() => conectarTiempoReal().catch(reconectar), espera);
    }

    async function iniciar() {
        if (!token) {
            enlaceInvalido();
            return;
        }
        // El contenedor debe estar visible antes de crear el mapa, o Leaflet mide su tamaño como cero.
        mostrar('contenido', true);
        crearMapa();
        let valido;
        try {
            valido = await cargar();
        } catch (e) {
            $('resumen').textContent = 'No se pudo cargar el seguimiento. Reintentando...';
            setTimeout(iniciar, 5000);
            return;
        }
        if (!valido) {
            return;
        }
        setInterval(pintarTextos, 1000);
        setInterval(() => {
            if (!vista.cliente) {
                cargar().catch(() => {
                });
            }
        }, REFRESCO_SIN_WEBSOCKET_MS);
        conectarTiempoReal().catch(reconectar);
    }

    window.addEventListener('online', () => {
        if (vista.datos && !vista.cliente) {
            vista.intentos = 0;
            conectarTiempoReal().catch(reconectar);
        }
    });

    iniciar();
})();
