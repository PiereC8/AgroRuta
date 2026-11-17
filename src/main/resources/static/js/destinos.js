// RFF-04: pantalla Destinos (precio esperado, flete y ubicación en el mapa).
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

    // Precios vigentes del destino como texto: «Palta Hass S/ 5.00 · Orégano S/ 8.00».
    const textoPrecios = (d) => (d.precios || []).map((p) => p.productoLote + ' ' + window.Ui.dinero(p.precioPorKg))
        .join(' · ');
    const precioDe = (d, producto) => (d.precios || []).find((p) => producto
        && p.productoLote.toLowerCase() === producto.trim().toLowerCase());

    // Muestra los precios ya fijados y, si hay uno solo, abre el formulario con él para cambiarlo.
    async function cambiarPrecio(destino) {
        const precios = destino.precios || [];
        const unico = precios.length === 1 ? precios[0] : {};
        const guardado = await window.Ui.panel({
            titulo: 'Precio en ' + destino.nombreDestino,
            texto: 'Cuánto esperas que te paguen por kilo de este producto en este destino. '
                + (precios.length ? 'Precios actuales: ' + textoPrecios(destino) + '. Para cambiar uno, escribe el '
                    + 'mismo producto.' : 'Este destino todavía no tiene precios.'),
            textoEnviar: 'Guardar precio',
            campos: [
                {id: 'productoLote', etiqueta: 'Producto', tipo: 'texto', requerido: true, marcador: 'Palta Hass',
                    ayuda: 'Escríbelo igual que en tus lotes.', valor: unico.productoLote},
                {id: 'precioPorKg', etiqueta: 'Precio por kilo (S/)', tipo: 'numero', requerido: true,
                    valor: unico.precioPorKg}
            ],
            nota: (v) => {
                const actual = precioDe(destino, v.productoLote);
                return actual ? 'Precio actual de ' + actual.productoLote + ': ' + window.Ui.dinero(actual.precioPorKg)
                    + ' por kilo; se reemplaza.' : '';
            },
            enviar: (v) => window.Api.pedir('/api/destinos/' + destino.idDestinoVenta + '/precio', {
                metodo: 'PUT', cuerpo: {productoLote: v.productoLote, precioPorKg: v.precioPorKg}
            })
        });
        if (guardado) {
            window.Ui.aviso('Precio guardado');
            cargar();
        }
    }

    async function cambiarFlete(destino) {
        const guardado = await window.Ui.panel({
            titulo: 'Flete a ' + destino.nombreDestino,
            texto: 'Cuánto cuesta transportar cada kilo hasta este destino. ' + (destino.costoFletePorKg === null
                || destino.costoFletePorKg === undefined ? 'Todavía no tiene flete.'
                : 'Flete actual: ' + window.Ui.dinero(destino.costoFletePorKg) + ' por kilo.'),
            textoEnviar: 'Guardar flete',
            campos: [{id: 'costoFletePorKg', etiqueta: 'Flete por kilo (S/)', tipo: 'numero', requerido: true,
                permiteCero: true, valor: destino.costoFletePorKg}],
            enviar: (v) => window.Api.pedir('/api/destinos/' + destino.idDestinoVenta + '/tarifa', {
                metodo: 'PUT', cuerpo: {costoFletePorKg: v.costoFletePorKg}
            })
        });
        if (guardado) {
            window.Ui.aviso('Flete guardado');
            cargar();
        }
    }

    // Rectángulo que contiene al Perú; el servidor rechaza puntos fuera de él (Coordenada.dentroDelPeru).
    const PERU = {latMin: -18.40, latMax: 0.05, lonMin: -81.40, lonMax: -68.60};
    const dentroDelPeru = (lat, lon) => lat >= PERU.latMin && lat <= PERU.latMax
        && lon >= PERU.lonMin && lon <= PERU.lonMax;

    function coordenada(minimo, maximo, nombre) {
        return (valor) => {
            const n = Number(valor.replace(',', '.'));
            return Number.isFinite(n) && n >= minimo && n <= maximo
                ? null : nombre + ' debe estar dentro del Perú (entre ' + minimo + ' y ' + maximo + ').';
        };
    }

    const sinTildes = (texto) => texto.normalize('NFD').replace(/[\u0300-\u036f]/g, '').toLowerCase();

    /** Dirección del punto elegido (OpenStreetMap). Devuelve el texto o null si no se pudo obtener. */
    async function direccionDe(lat, lon) {
        const url = 'https://nominatim.openstreetmap.org/reverse?format=json&zoom=18&accept-language=es&lat='
            + encodeURIComponent(lat) + '&lon=' + encodeURIComponent(lon);
        try {
            const respuesta = await fetch(url, {headers: {'Accept': 'application/json'}});
            return respuesta.ok ? ((await respuesta.json()).display_name || null) : null;
        } catch (e) {
            return null;
        }
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
        const direccion = el('p', 'suave', 'Haz clic en el mapa o arrastra el marcador hasta el lugar exacto.');
        caja.append(busqueda, resultados, mapa, direccion);

        let vista = null;
        let marcador = null;
        let consulta = 0;
        const campo = (id) => document.getElementById('campo-' + id);

        /** Muestra la dirección del punto y avisa si no parece estar en la ciudad del destino. */
        async function mostrarDireccion(lat, lon) {
            const numero = ++consulta;
            direccion.className = 'suave';
            direccion.textContent = 'Buscando la dirección del punto…';
            const texto = await direccionDe(lat, lon);
            if (numero !== consulta) {
                return;
            }
            if (!texto) {
                direccion.textContent = 'No se pudo obtener la dirección. Revisa que el punto sea el correcto.';
                return;
            }
            direccion.textContent = 'Punto elegido: ' + texto;
            if (!sinTildes(texto).includes(sinTildes(destino.ciudadDestino))) {
                direccion.className = 'dato-perdida';
                direccion.textContent += '. Ojo: no parece estar en ' + destino.ciudadDestino + '.';
            }
        }

        function poner(lat, lon, acercar, zoom) {
            if (!dentroDelPeru(lat, lon)) {
                direccion.className = 'dato-perdida';
                direccion.textContent = 'Ese punto está fuera del Perú. Elige un lugar dentro del país.';
                return;
            }
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
                vista.setView(punto, Math.max(vista.getZoom(), zoom || 16));
            }
            campo('latitud').value = String(punto[0]);
            campo('longitud').value = String(punto[1]);
            mostrarDireccion(punto[0], punto[1]);
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
                let soloCiudad = false;
                if (lugares.length === 0 && elegirPrimero) {
                    lugares = await buscarLugar(destino.ciudadDestino);
                    soloCiudad = true;
                }
                if (lugares.length === 0) {
                    resultados.appendChild(el('li', 'suave', 'No se encontró. Marca el punto en el mapa.'));
                    return;
                }
                if (elegirPrimero) {
                    // Al abrir, el destino ya queda marcado en el mapa (se puede mover antes de guardar).
                    poner(lugares[0].lat, lugares[0].lon, true, soloCiudad ? 13 : 16);
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

        /** guardada: {latitud, longitud} ya fijados para el destino, o null si aún no tiene ubicación. */
        function iniciar(guardada) {
            const limites = L.latLngBounds([PERU.latMin, PERU.lonMin], [PERU.latMax, PERU.lonMax]);
            vista = L.map(mapa, {maxBounds: limites.pad(0.05), maxBoundsViscosity: 1.0, minZoom: 5})
                .setView([-16.4, -71.5], 6);
            L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 19,
                attribution: '&copy; OpenStreetMap'}).addTo(vista);
            vista.on('click', (evento) => poner(evento.latlng.lat, evento.latlng.lng, false));
            const alEscribir = () => {
                const lat = Number(campo('latitud').value.replace(',', '.'));
                const lon = Number(campo('longitud').value.replace(',', '.'));
                if (Number.isFinite(lat) && Number.isFinite(lon) && dentroDelPeru(lat, lon)
                    && campo('latitud').value && campo('longitud').value) {
                    poner(lat, lon, true);
                }
            };
            campo('latitud').addEventListener('change', alEscribir);
            campo('longitud').addEventListener('change', alEscribir);
            setTimeout(() => vista.invalidateSize(), 300);
            if (guardada && guardada.latitud !== null && guardada.latitud !== undefined) {
                poner(guardada.latitud, guardada.longitud, true);
            } else {
                ejecutarBusqueda(true);
            }
        }

        return {caja, iniciar, cerrar: () => vista && vista.remove()};
    }

    async function cambiarUbicacion(destino) {
        let guardada = null;
        try {
            guardada = await window.Api.pedir('/api/destinos/' + destino.idDestinoVenta + '/coordenadas');
        } catch (error) {
            guardada = null;
        }
        const selector = selectorEnMapa(destino);
        const abierto = window.Ui.panel({
            titulo: 'Ubicación de ' + destino.nombreDestino,
            texto: 'Con la ubicación el comprador ve el avance del viaje y la hora estimada de llegada.',
            textoEnviar: 'Guardar ubicación',
            ancho: true,
            contenido: selector.caja,
            campos: [
                {id: 'latitud', etiqueta: 'Latitud', tipo: 'texto', requerido: true, marcador: '-18.0105',
                    validar: coordenada(PERU.latMin, PERU.latMax, 'La latitud'), ayuda: 'Se llena sola al marcar el punto en el mapa.'},
                {id: 'longitud', etiqueta: 'Longitud', tipo: 'texto', requerido: true, marcador: '-70.2486',
                    validar: coordenada(PERU.lonMin, PERU.lonMax, 'La longitud')}
            ],
            enviar: (v) => window.Api.pedir('/api/destinos/' + destino.idDestinoVenta + '/coordenadas', {
                metodo: 'PUT',
                cuerpo: {latitud: Number(v.latitud.replace(',', '.')), longitud: Number(v.longitud.replace(',', '.'))}
            })
        });
        selector.iniciar(guardada);
        const guardado = await abierto;
        selector.cerrar();
        if (guardado) {
            window.Ui.aviso('Ubicación guardada');
        }
    }

    // RFF-23: búsqueda y filtros de destinos, en el navegador.
    let destinos = [];
    const filtros = window.Ui.barraFiltros($('barra-filtros'), [
        {id: 'texto', tipo: 'busqueda', etiqueta: 'Buscar', marcador: 'Nombre o ciudad…'},
        {id: 'zona', tipo: 'select', etiqueta: 'Zona', opciones: [['LOCAL', 'Local'], ['TRANSPORTE', 'Con transporte']]},
        {id: 'ciudad', tipo: 'select', etiqueta: 'Ciudad', todos: 'Todas'}
    ], pintar);

    function pintar() {
        const f = filtros.valores();
        const visibles = destinos.filter((d) => window.Ui.coincide(f.texto, d.nombreDestino, d.ciudadDestino)
            && (!f.zona || (f.zona === 'LOCAL') === Boolean(d.esLocal))
            && (!f.ciudad || d.ciudadDestino === f.ciudad));
        filtros.contar(visibles.length, destinos.length);
        const filtrando = Object.values(f).some(Boolean);
        window.Ui.tabla($('lista'), [
            {titulo: 'Destino', clase: 'largo', valor: (d) => d.nombreDestino},
            {titulo: 'Ciudad', clase: 'largo', valor: (d) => d.ciudadDestino},
            {titulo: 'Zona', valor: (d) => window.Ui.etiqueta(d.esLocal ? 'Local' : 'Con transporte',
                d.esLocal ? '' : 'neutro')},
            {titulo: 'Flete/kg', valor: (d) => d.costoFletePorKg !== null && d.costoFletePorKg !== undefined
                ? window.Ui.dinero(d.costoFletePorKg)
                : d.esLocal ? 'No aplica' : window.Ui.etiqueta('Sin flete', 'espera')},
            {titulo: 'Precios/kg', clase: 'largo', valor: (d) => textoPrecios(d)
                || window.Ui.etiqueta('Sin precios', 'espera')}
        ], visibles, {
            vacio: filtrando
                ? {titulo: 'Ningún destino coincide', texto: 'Cambia la búsqueda o pulsa «Limpiar».'}
                : {titulo: 'Aún no hay destinos',
                    texto: puedeEscribir ? 'Registra el primer lugar donde vendes.' : 'El Dueño del negocio los registra.'},
            acciones: puedeEscribir ? (d) => [
                window.Ui.boton('Precio', 'boton-secundario', () => cambiarPrecio(d)),
                window.Ui.boton('Flete', 'boton-secundario', () => cambiarFlete(d)),
                window.Ui.boton('Ubicación', 'boton-secundario', () => cambiarUbicacion(d))
            ] : null
        });
    }

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            destinos = await window.Api.pedir('/api/destinos');
            filtros.opciones('ciudad', window.Ui.opcionesUnicas(destinos, (d) => d.ciudadDestino));
            pintar();
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    $('accion-principal').classList.toggle('oculto', !puedeEscribir);
    $('accion-principal').addEventListener('click', registrar);
    window.Ui.alReintentar(cargar);
    cargar();
})();
