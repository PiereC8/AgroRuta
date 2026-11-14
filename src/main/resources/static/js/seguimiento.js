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
        capa: null,
        encuadrado: false,
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

    function crearMapa() {
        vista.mapa = L.map('mapa', {zoomControl: true}).setView([-12.05, -77.04], 6);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {
            maxZoom: 19,
            attribution: '&copy; Colaboradores de OpenStreetMap'
        }).addTo(vista.mapa);
        vista.trazo = L.polyline([], {color: '#2f7d32', weight: 4, opacity: 0.8}).addTo(vista.mapa);
        vista.capa = new window.RutaSimulada.Capa(vista.mapa);
        // Si el contenedor cambia de tamaño (pestaña en segundo plano, girar el celular), el mapa se reajusta.
        if ('ResizeObserver' in window) {
            new ResizeObserver(() => vista.mapa.invalidateSize()).observe($('mapa'));
        }
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
        vista.puntos.push([latitud, longitud]);
        // Con ruta, el camino que falta reemplaza al recorrido ya hecho: este se borra a medida que se avanza.
        vista.trazo.setLatLngs(vista.capa.tieneRuta() ? [] : vista.puntos);
        vista.ultimaFecha = Date.parse(fechaIso);
        vista.capa.fijarPosicion(latitud, longitud, vista.ultimaFecha);
        if (!vista.capa.tieneRuta()) {
            encuadrar();
        }
    }

    function encuadrar() {
        const limites = vista.capa.limites();
        if (limites) {
            vista.mapa.fitBounds(limites, {padding: [40, 40], maxZoom: 16});
            return;
        }
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
        const sim = vista.capa.pintar(ahoraServidor());
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
            if (datos.conRetraso) {
                resumen.textContent = 'Tu pedido está en camino, pero viene con retraso respecto de lo previsto.';
                resumen.className = 'mensaje aviso';
            }
            if (sim && sim.llegadaMs != null) {
                const minutos = Math.max(1, Math.round((sim.llegadaMs - ahoraServidor()) / 60000));
                $('dato-eta').textContent = 'hacia las ' + formatoHora(sim.llegadaMs) + ' (en unos ' + minutos
                    + ' min)' + (sim.estimado ? ', estimado' : '');
            } else if (vista.eta != null && vista.etaRecibidaMs != null) {
                const llegada = vista.etaRecibidaMs + vista.eta * 60000;
                $('dato-eta').textContent = 'en unos ' + vista.eta + ' min (' + formatoHora(llegada) + ')';
            } else {
                $('dato-eta').textContent = 'Aún no se puede calcular';
            }
        }

        if (vista.ultimaFecha) {
            const segundos = Math.max(0, Math.round((ahoraServidor() - vista.ultimaFecha) / 1000));
            $('dato-actualizacion').textContent = 'ubicación real ' + (segundos < 90 ? 'hace ' + segundos + ' s'
                : 'hace ' + Math.round(segundos / 60) + ' min');
            if (datos.estadoEnvio === 'EN_RUTA' && segundos > SEGUNDOS_SIN_SENAL && !datos.llegoAlDestino) {
                const hace = Math.round(segundos / 60);
                aviso.textContent = sim && sim.estimado
                    ? 'Sin señal del vehículo desde hace ' + hace + ' min. El punto con borde punteado es una '
                    + 'estimación de por dónde va según la ruta; se corrige cuando vuelva la señal.'
                    + (sim.enTope ? ' Ya casi llega: esperamos su ubicación real para confirmar.' : '')
                    : 'Sin señal del vehículo desde hace ' + hace + ' min. Mostramos su última posición conocida.';
                aviso.classList.remove('oculto');
            }
        } else {
            $('dato-actualizacion').textContent = '-';
        }
    }

    function aplicarDatos(datos) {
        vista.datos = datos;
        vista.desfaseMs = Date.parse(datos.ahora) - Date.now();
        vista.eta = datos.etaMinutos;
        vista.etaRecibidaMs = ahoraServidor();
        dibujarDestino(datos);
        const rutaCambio = vista.capa.fijarRuta(datos.ruta, datos.velocidadKmh);
        vista.capa.activar(datos.estadoEnvio === 'EN_RUTA' && !datos.llegoAlDestino);
        vista.puntos = [];
        for (const p of datos.trazoReciente) {
            agregarPosicion(p.latitud, p.longitud, p.fechaPosicion);
        }
        if (!vista.encuadrado || rutaCambio) {
            encuadrar();
            vista.encuadrado = true;
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
        } else if (cuerpo.tipo === 'RUTA') {
            // La ruta se calculó o cambió: se pide completa.
            cargar().catch(() => {
            });
        } else if (cuerpo.tipo === 'ALERTA' && cuerpo.tipoAlerta === 'LLEGADA') {
            vista.datos.llegoAlDestino = true;
            vista.capa.activar(false);
        } else if (cuerpo.tipo === 'ALERTA' && cuerpo.tipoAlerta === 'RETRASO') {
            vista.datos.conRetraso = true;
        }
        pintarTextos();
    }

    async function conectarTiempoReal() {
        const cliente = new ClienteStomp(window.AgroRutaConfig.urlWebSocket, {token}, {
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
