'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'destinos', titulo: () => 'Destinos'})) {
        return;
    }

    const $ = (id) => document.getElementById(id);
    const el = window.Ui.el;
    const puedeEscribir = window.Ui.puedeEscribir();

    async function registrar() {
        const creado = await window.Ui.panel({
            titulo: 'Registrar destino',
            textoEnviar: 'Registrar destino',
            campos: [
                {id: 'nombreDestino', etiqueta: 'Nombre del destino', tipo: 'texto', requerido: true,
                    ayuda: 'Por ejemplo: Mercado Mayorista de Lima.'},
                {id: 'ciudadDestino', etiqueta: 'Ciudad', tipo: 'texto', requerido: true},
                {id: 'esLocal', etiqueta: '¿Está en tu misma zona?', tipo: 'select', requerido: true,
                    opciones: [{valor: 'false', texto: 'No, hay que transportarlo'}, {valor: 'true', texto: 'Sí, es local'}]}
            ],
            enviar: (v) => window.Api.pedir('/api/destinos', {
                metodo: 'POST',
                cuerpo: {nombreDestino: v.nombreDestino, ciudadDestino: v.ciudadDestino, esLocal: v.esLocal === 'true'}
            })
        });
        if (creado) {
            window.Ui.aviso('Destino registrado');
            cargar();
        }
    }

    async function cambiarPrecio(destino) {
        const guardado = await window.Ui.panel({
            titulo: 'Precio en ' + destino.nombreDestino,
            texto: 'Cuánto esperas que te paguen por kilo de este producto en este destino.',
            textoEnviar: 'Guardar precio',
            campos: [
                {id: 'productoLote', etiqueta: 'Producto', tipo: 'texto', requerido: true, marcador: 'Palta Hass',
                    ayuda: 'Escríbelo igual que en tus lotes.'},
                {id: 'precioPorKg', etiqueta: 'Precio por kilo (S/)', tipo: 'numero', requerido: true}
            ],
            enviar: (v) => window.Api.pedir('/api/destinos/' + destino.idDestinoVenta + '/precio', {
                metodo: 'PUT', cuerpo: {productoLote: v.productoLote, precioPorKg: v.precioPorKg}
            })
        });
        if (guardado) {
            window.Ui.aviso('Precio guardado');
        }
    }

    async function cambiarFlete(destino) {
        const guardado = await window.Ui.panel({
            titulo: 'Flete a ' + destino.nombreDestino,
            texto: 'Cuánto cuesta transportar cada kilo hasta este destino.',
            textoEnviar: 'Guardar flete',
            campos: [{id: 'costoFletePorKg', etiqueta: 'Flete por kilo (S/)', tipo: 'numero', requerido: true,
                permiteCero: true}],
            enviar: (v) => window.Api.pedir('/api/destinos/' + destino.idDestinoVenta + '/tarifa', {
                metodo: 'PUT', cuerpo: {costoFletePorKg: v.costoFletePorKg}
            })
        });
        if (guardado) {
            window.Ui.aviso('Flete guardado');
        }
    }

    function coordenada(maximo, nombre) {
        return (valor) => {
            const n = Number(valor.replace(',', '.'));
            return Number.isFinite(n) && Math.abs(n) <= maximo
                ? null : nombre + ' debe estar entre -' + maximo + ' y ' + maximo + '.';
        };
    }

    /** Busca un lugar en OpenStreetMap (solo en Perú). Devuelve [{nombre, lat, lon}]. */
    async function buscarLugar(texto) {
        const url = 'https://nominatim.openstreetmap.org/search?format=json&limit=5&countrycodes=pe&accept-language=es&q='
            + encodeURIComponent(texto);
        const respuesta = await fetch(url, {headers: {'Accept': 'application/json'}});
        if (!respuesta.ok) {
            throw new Error('No se pudo buscar el lugar. Marca el punto en el mapa.');
        }
        return (await respuesta.json()).map((r) => ({nombre: r.display_name, lat: Number(r.lat), lon: Number(r.lon)}));
    }

    /** Mapa para elegir el punto: clic o arrastrar el marcador llena latitud y longitud. */
    function selectorEnMapa(destino) {
        const caja = el('div');
        const busqueda = el('div', 'mapa-busqueda');
        const entrada = el('input', 'campo__entrada');
        entrada.type = 'search';
        entrada.placeholder = 'Busca el mercado o la dirección…';
        entrada.setAttribute('aria-label', 'Buscar un lugar en el mapa');
        entrada.value = destino.nombreDestino + ', ' + destino.ciudadDestino;
        const buscar = window.Ui.boton('Buscar', 'boton-secundario');
        busqueda.append(entrada, buscar);
        const resultados = el('ul', 'mapa-resultados');
        const mapa = el('div', 'mapa-elegir');
        caja.append(busqueda, resultados, mapa,
            el('p', 'suave', 'Haz clic en el mapa o arrastra el marcador hasta el lugar exacto.'));

        let vista = null;
        let marcador = null;
        const campo = (id) => document.getElementById('campo-' + id);

        function poner(lat, lon, acercar) {
            const punto = [Number(lat.toFixed(6)), Number(lon.toFixed(6))];
            if (marcador) {
                marcador.setLatLng(punto);
            } else {
                marcador = L.marker(punto, {draggable: true, icon: L.divIcon({className: '',
                    html: '<div class="marcador-destino marcador-destino--grande"></div>', iconSize: [24, 24]})})
                    .addTo(vista);
                marcador.on('dragend', () => {
                    const p = marcador.getLatLng();
                    poner(p.lat, p.lng, false);
                });
            }
            if (acercar) {
                vista.setView(punto, Math.max(vista.getZoom(), 16));
            }
            campo('latitud').value = String(punto[0]);
            campo('longitud').value = String(punto[1]);
        }

        async function ejecutarBusqueda(elegirPrimero) {
            const texto = entrada.value.trim();
            if (!texto) {
                return;
            }
            buscar.disabled = true;
            resultados.replaceChildren();
            try {
                let lugares = await buscarLugar(texto);
                if (lugares.length === 0 && elegirPrimero) {
                    lugares = await buscarLugar(destino.ciudadDestino);
                }
                if (lugares.length === 0) {
                    resultados.appendChild(el('li', 'suave', 'No se encontró. Marca el punto en el mapa.'));
                    return;
                }
                if (elegirPrimero) {
                    vista.setView([lugares[0].lat, lugares[0].lon], 15);
                    return;
                }
                for (const lugar of lugares) {
                    const li = el('li');
                    li.appendChild(window.Ui.boton(lugar.nombre, '', () => {
                        resultados.replaceChildren();
                        poner(lugar.lat, lugar.lon, true);
                    }));
                    li.firstChild.className = '';
                    resultados.appendChild(li);
                }
            } catch (error) {
                resultados.appendChild(el('li', 'suave', error.message));
            } finally {
                buscar.disabled = false;
            }
        }

        buscar.addEventListener('click', () => ejecutarBusqueda(false));
        entrada.addEventListener('keydown', (evento) => {
            if (evento.key === 'Enter') {
                evento.preventDefault();
                ejecutarBusqueda(false);
            }
        });

        function iniciar() {
            vista = L.map(mapa).setView([-16.4, -71.5], 6);
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 19,
                attribution: '&copy; OpenStreetMap'}).addTo(vista);
            vista.on('click', (evento) => poner(evento.latlng.lat, evento.latlng.lng, false));
            const alEscribir = () => {
                const lat = Number(campo('latitud').value.replace(',', '.'));
                const lon = Number(campo('longitud').value.replace(',', '.'));
                if (Number.isFinite(lat) && Number.isFinite(lon) && Math.abs(lat) <= 90 && Math.abs(lon) <= 180
                    && campo('latitud').value && campo('longitud').value) {
                    poner(lat, lon, true);
                }
            };
            campo('latitud').addEventListener('change', alEscribir);
            campo('longitud').addEventListener('change', alEscribir);
            setTimeout(() => vista.invalidateSize(), 300);
            ejecutarBusqueda(true);
        }

        return {caja, iniciar, cerrar: () => vista && vista.remove()};
    }

    async function cambiarUbicacion(destino) {
        const selector = selectorEnMapa(destino);
        const abierto = window.Ui.panel({
            titulo: 'Ubicación de ' + destino.nombreDestino,
            texto: 'Con la ubicación el comprador ve el avance del viaje y la hora estimada de llegada.',
            textoEnviar: 'Guardar ubicación',
            ancho: true,
            contenido: selector.caja,
            campos: [
                {id: 'latitud', etiqueta: 'Latitud', tipo: 'texto', requerido: true, marcador: '-18.0105',
                    validar: coordenada(90, 'La latitud'), ayuda: 'Se llena sola al marcar el punto en el mapa.'},
                {id: 'longitud', etiqueta: 'Longitud', tipo: 'texto', requerido: true, marcador: '-70.2486',
                    validar: coordenada(180, 'La longitud')}
            ],
            enviar: (v) => window.Api.pedir('/api/destinos/' + destino.idDestinoVenta + '/coordenadas', {
                metodo: 'PUT',
                cuerpo: {latitud: Number(v.latitud.replace(',', '.')), longitud: Number(v.longitud.replace(',', '.'))}
            })
        });
        selector.iniciar();
        const guardado = await abierto;
        selector.cerrar();
        if (guardado) {
            window.Ui.aviso('Ubicación guardada');
        }
    }

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            const destinos = await window.Api.pedir('/api/destinos');
            window.Ui.tabla($('lista'), [
                {titulo: 'Destino', clase: 'largo', valor: (d) => d.nombreDestino},
                {titulo: 'Ciudad', clase: 'largo', valor: (d) => d.ciudadDestino},
                {titulo: 'Zona', valor: (d) => window.Ui.etiqueta(d.esLocal ? 'Local' : 'Con transporte',
                    d.esLocal ? '' : 'neutro')}
            ], destinos, {
                vacio: {titulo: 'Aún no hay destinos',
                    texto: puedeEscribir ? 'Registra el primer lugar donde vendes.' : 'El Dueño del negocio los registra.'},
                acciones: puedeEscribir ? (d) => [
                    window.Ui.boton('Precio', 'boton-secundario', () => cambiarPrecio(d)),
                    window.Ui.boton('Flete', 'boton-secundario', () => cambiarFlete(d)),
                    window.Ui.boton('Ubicación', 'boton-secundario', () => cambiarUbicacion(d))
                ] : null
            });
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    $('accion-principal').classList.toggle('oculto', !puedeEscribir);
    $('accion-principal').addEventListener('click', registrar);
    window.Ui.alReintentar(cargar);
    cargar();
})();
