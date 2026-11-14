'use strict';

/**
 * Dibuja el camino que le falta al vehículo y mueve su marcador sobre él.
 *
 * - Con señal: el marcador está en la última ubicación real y el camino que falta sale de ahí.
 * - Sin señal (más de SEGUNDOS_SIN_SENAL sin ubicación nueva): el marcador sigue avanzando sobre la ruta, con la
 *   velocidad que traía, como una ESTIMACIÓN. Se frena al 95 % de la ruta: la llegada no se da por hecha.
 * - Al llegar una ubicación real, el marcador pasa a ella y el camino se recalcula.
 *
 * La geometría es la misma que usa el servidor (Trayecto.medirDesde) para que las dos cuentas coincidan.
 */
(function () {
    const RADIO_TIERRA = 6371000;
    const SEGUNDOS_SIN_SENAL = 60;
    const TOPE_SIMULACION = 0.95;
    const VELOCIDAD_MINIMA_KMH = 5;
    const COLOR = '#1E7F4B';

    const rad = (grados) => grados * Math.PI / 180;

    function distancia(a, b) {
        const dLat = rad(b[0] - a[0]);
        const dLon = rad(b[1] - a[1]);
        const h = Math.sin(dLat / 2) ** 2 + Math.cos(rad(a[0])) * Math.cos(rad(b[0])) * Math.sin(dLon / 2) ** 2;
        return 2 * RADIO_TIERRA * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    /** puntos: [[lat, lon], ...]. Guarda el largo acumulado hasta cada punto. */
    function prepararRuta(puntos) {
        const acumulado = [0];
        for (let i = 1; i < puntos.length; i++) {
            acumulado.push(acumulado[i - 1] + distancia(puntos[i - 1], puntos[i]));
        }
        return {puntos, acumulado, total: acumulado[acumulado.length - 1]};
    }

    /** Dónde cae un punto sobre la ruta: metros recorridos hasta ahí y a qué distancia de la línea está. */
    function medir(ruta, lat, lon) {
        const coseno = Math.cos(rad(lat));
        let mejorDesvio = Infinity;
        let mejorRecorrido = 0;
        for (let i = 1; i < ruta.puntos.length; i++) {
            const a = ruta.puntos[i - 1];
            const b = ruta.puntos[i];
            const ax = rad(a[1] - lon) * coseno * RADIO_TIERRA;
            const ay = rad(a[0] - lat) * RADIO_TIERRA;
            const dx = rad(b[1] - lon) * coseno * RADIO_TIERRA - ax;
            const dy = rad(b[0] - lat) * RADIO_TIERRA - ay;
            const cuadrado = dx * dx + dy * dy;
            const t = cuadrado === 0 ? 0 : Math.max(0, Math.min(1, -(ax * dx + ay * dy) / cuadrado));
            const desvio = Math.hypot(ax + t * dx, ay + t * dy);
            if (desvio < mejorDesvio) {
                mejorDesvio = desvio;
                mejorRecorrido = ruta.acumulado[i - 1] + t * (ruta.acumulado[i] - ruta.acumulado[i - 1]);
            }
        }
        return {recorrido: mejorRecorrido, desvio: mejorDesvio};
    }

    /** Punto de la ruta a tantos metros del inicio, y el índice del tramo donde cae. */
    function puntoA(ruta, metros) {
        const m = Math.max(0, Math.min(ruta.total, metros));
        let i = 1;
        while (i < ruta.acumulado.length - 1 && ruta.acumulado[i] < m) {
            i++;
        }
        const largo = ruta.acumulado[i] - ruta.acumulado[i - 1];
        const t = largo === 0 ? 0 : (m - ruta.acumulado[i - 1]) / largo;
        const a = ruta.puntos[i - 1];
        const b = ruta.puntos[i];
        return {punto: [a[0] + (b[0] - a[0]) * t, a[1] + (b[1] - a[1]) * t], indice: i};
    }

    class Capa {
        constructor(mapa) {
            this.mapa = mapa;
            this.ruta = null;
            this.claveRuta = null;
            this.velocidadKmh = null;
            this.ultima = null;
            this.activa = false;
            this.modo = null;
            this.linea = L.polyline([], {color: COLOR, weight: 5, opacity: 0.85}).addTo(mapa);
            this.marcador = null;
        }

        /** Devuelve true si la ruta es distinta de la que había (para volver a encuadrar el mapa). */
        fijarRuta(dto, velocidadKmh) {
            this.velocidadKmh = velocidadKmh;
            if (!dto || !dto.puntos || dto.puntos.length < 2) {
                const habia = this.ruta !== null;
                this.ruta = null;
                this.claveRuta = null;
                return habia;
            }
            const clave = String(dto.fechaCalculo);
            if (clave === this.claveRuta) {
                return false;
            }
            this.claveRuta = clave;
            this.ruta = prepararRuta(dto.puntos);
            return true;
        }

        fijarPosicion(latitud, longitud, fechaMs) {
            if (!this.ultima || fechaMs >= this.ultima.fechaMs) {
                this.ultima = {latitud, longitud, fechaMs};
            }
        }

        /** Mientras no está activa (entregado, llegó) no se dibuja el camino que falta ni se estima nada. */
        activar(activa) {
            this.activa = activa;
        }

        tieneRuta() {
            return this.ruta !== null;
        }

        limites() {
            return this.ruta ? L.latLngBounds(this.ruta.puntos) : null;
        }

        icono(estimado) {
            return L.divIcon({
                className: 'marcador-suave',
                html: '<div class="marcador-vehiculo' + (estimado ? ' marcador-estimado' : '') + '"></div>',
                iconSize: [18, 18]
            });
        }

        ponerMarcador(punto, estimado) {
            if (!this.marcador) {
                this.marcador = L.marker(punto, {icon: this.icono(estimado), interactive: false, keyboard: false})
                    .addTo(this.mapa);
                this.modo = estimado;
                return;
            }
            this.marcador.setLatLng(punto);
            if (this.modo !== estimado) {
                this.marcador.setIcon(this.icono(estimado));
                this.modo = estimado;
            }
        }

        /**
         * Actualiza marcador y camino para el instante dado (hora del servidor, en ms) y devuelve qué se muestra:
         * {estimado, edadSegundos, restanteMetros, llegadaMs, enTope}. Sin ubicación todavía devuelve null.
         */
        pintar(ahoraMs) {
            if (!this.ultima) {
                return null;
            }
            const u = this.ultima;
            const edad = Math.max(0, (ahoraMs - u.fechaMs) / 1000);
            if (!this.ruta || !this.activa) {
                this.ponerMarcador([u.latitud, u.longitud], false);
                this.linea.setLatLngs([]);
                return {estimado: false, edadSegundos: edad, restanteMetros: null, llegadaMs: null, enTope: false};
            }

            const ruta = this.ruta;
            const base = medir(ruta, u.latitud, u.longitud).recorrido;
            const estimado = edad >= SEGUNDOS_SIN_SENAL;
            const velocidad = Math.max(this.velocidadKmh || 0, VELOCIDAD_MINIMA_KMH) / 3.6;
            let recorrido = base;
            let enTope = false;
            let punto = [u.latitud, u.longitud];
            if (estimado) {
                const tope = Math.max(base, ruta.total * TOPE_SIMULACION);
                recorrido = Math.min(base + velocidad * edad, tope);
                enTope = recorrido >= tope && base < tope;
                punto = puntoA(ruta, recorrido).punto;
            }
            this.ponerMarcador(punto, estimado);

            const desde = puntoA(ruta, recorrido);
            this.linea.setLatLngs([punto].concat(ruta.puntos.slice(desde.indice)));
            this.linea.setStyle({dashArray: estimado ? '10 10' : null});

            const restante = Math.max(0, ruta.total - recorrido);
            return {
                estimado,
                edadSegundos: edad,
                restanteMetros: restante,
                llegadaMs: ahoraMs + restante / velocidad * 1000,
                enTope
            };
        }
    }

    window.RutaSimulada = {Capa, prepararRuta, medir, puntoA, distancia};
})();
