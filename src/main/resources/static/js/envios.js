'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'envios', titulo: () => 'Envíos'})) {
        return;
    }

    const $ = (id) => document.getElementById(id);
    const el = window.Ui.el;
    const ESTADOS = {PROGRAMADO: ['Programado', 'espera'], EN_RUTA: ['En ruta', ''], ENTREGADO: ['Entregado', 'neutro']};
    const FILTROS = [['', 'Todos'], ['PROGRAMADO', 'Programados'], ['EN_RUTA', 'En ruta'], ['ENTREGADO', 'Entregados']];
    const ALERTAS = {GPS_DESACTIVADO: 'El conductor desactivó el GPS', DETENIDO: 'Lleva mucho tiempo detenido',
        LLEGADA: 'Llegó al destino'};
    const REFRESCO_MAPA_MS = 15000;
    const TOLERANCIA_KG = 0.0005;

    let envios = [];
    let pedidos = [];
    let compradores = [];
    let destinos = [];
    let vehiculos = [];
    let filtro = '';

    const pedidoDe = (id) => pedidos.find((p) => p.idPedido === id);
    const compradorDe = (id) => (compradores.find((c) => c.idComprador === id) || {}).nombreComprador || '—';
    const destinoDe = (id) => (destinos.find((d) => d.idDestinoVenta === id) || {}).nombreDestino || '—';
    const placaDe = (id) => (vehiculos.find((v) => v.idVehiculo === id) || {}).placaVehiculo || '—';

    function resumenPedido(envio) {
        const p = pedidoDe(envio.idPedido);
        return p ? 'Pedido ' + p.idPedido + ' · ' + compradorDe(p.idComprador) : 'Pedido ' + envio.idPedido;
    }

    function novedad(e) {
        if (e.estadoEnvio === 'ENTREGADO') {
            return 'Entregado ' + window.Ui.fecha(e.fechaEntrega);
        }
        if (e.estadoEnvio === 'EN_RUTA') {
            return 'Salió ' + window.Ui.fecha(e.fechaSalida);
        }
        return 'Programado ' + window.Ui.fecha(e.fechaProgramada);
    }

    async function despachar() {
        const libres = pedidos.filter((p) => p.estadoPedido === 'PENDIENTE');
        const ocupados = new Set(envios.filter((e) => e.estadoEnvio !== 'ENTREGADO').map((e) => e.idVehiculo));
        const disponibles = vehiculos.filter((v) => !ocupados.has(v.idVehiculo));
        if (libres.length === 0) {
            return avisarFalta('No hay pedidos por despachar', 'Registra un pedido en la sección Pedidos.');
        }
        if (disponibles.length === 0) {
            return avisarFalta('No hay vehículos libres',
                'Todos tus vehículos están en un envío. Espera a que terminen o registra otro en Transporte.');
        }
        const creado = await window.Ui.panel({
            titulo: 'Despachar un pedido',
            textoEnviar: 'Despachar',
            campos: [
                {id: 'idPedido', etiqueta: 'Pedido', tipo: 'select', requerido: true,
                    opciones: libres.map((p) => ({valor: p.idPedido, texto: 'Pedido ' + p.idPedido + ' · '
                        + compradorDe(p.idComprador) + ' · ' + window.Ui.kilos(p.cantidadKg)}))},
                {id: 'idVehiculo', etiqueta: 'Vehículo', tipo: 'select', requerido: true,
                    ayuda: 'Solo aparecen los vehículos que no están en un envío.',
                    opciones: disponibles.map((v) => ({valor: v.idVehiculo, texto: v.placaVehiculo + ' · carga '
                        + window.Ui.kilos(v.capacidadKg)})),
                    validar: (valor, todos) => {
                        const p = libres.find((x) => String(x.idPedido) === todos.idPedido);
                        const v = disponibles.find((x) => String(x.idVehiculo) === valor);
                        return p && v && p.cantidadKg > v.capacidadKg
                            ? 'El pedido pesa ' + window.Ui.kilos(p.cantidadKg) + ' y este vehículo solo carga '
                            + window.Ui.kilos(v.capacidadKg) + '.' : null;
                    }}
            ],
            enviar: (v) => window.Api.pedir('/api/envios', {metodo: 'POST',
                cuerpo: {idPedido: Number(v.idPedido), idVehiculo: Number(v.idVehiculo)}})
        });
        if (creado) {
            window.Ui.aviso('Envío programado');
            cargar();
        }
    }

    async function avisarFalta(titulo, texto) {
        await window.Ui.detalle({titulo, texto, contenido: el('span')});
    }

    async function iniciarRuta(envio) {
        const acepto = await window.Ui.confirmar({
            titulo: 'Iniciar la ruta del envío ' + envio.idEnvio,
            texto: 'El vehículo ' + placaDe(envio.idVehiculo) + ' sale hacia ' + destinoDe((pedidoDe(envio.idPedido) || {}).idDestinoVenta)
                + '. Desde este momento se puede seguir en el mapa.',
            textoAceptar: 'Iniciar ruta',
            enviar: () => window.Api.pedir('/api/envios/' + envio.idEnvio + '/salida', {metodo: 'POST'})
        });
        if (acepto) {
            window.Ui.aviso('El envío salió en ruta');
            cargar();
        }
    }

    function cifra(titulo, valor, clase) {
        const t = el('div', 'tarjeta');
        t.append(el('div', 'tarjeta__titulo', titulo), el('div', 'cifra ' + (clase || ''), valor));
        return t;
    }

    function mostrarLiquidacion(l) {
        const contenido = el('div');
        const cifras = el('div', 'cifras-liquidacion');
        cifras.append(cifra('Ingreso real', window.Ui.dinero(l.ingresoReal)),
            cifra('Flete', window.Ui.dinero(l.costoFleteTotal)),
            cifra('Ganancia real', window.Ui.dinero(l.gananciaReal)),
            cifra('Ganancia esperada', window.Ui.dinero(l.gananciaEsperada)));
        contenido.appendChild(cifras);
        const perdida = el('p', null, 'Pérdida por merma y rechazo: ');
        perdida.appendChild(el('strong', 'dato-perdida', window.Ui.dinero(l.perdidaPorMermaYRechazo)));
        contenido.append(perdida, el('p', 'suave', 'Despachado ' + window.Ui.kilos(l.cantidadDespachadaKg)
            + ' · recibido ' + window.Ui.kilos(l.cantidadRecibidaKg) + ' · merma ' + window.Ui.kilos(l.mermaKg)
            + ' · rechazo ' + window.Ui.kilos(l.rechazoKg)));
        return window.Ui.detalle({titulo: 'Liquidación del envío ' + l.idEnvio, contenido});
    }

    async function registrarEntrega(envio) {
        const pedido = pedidoDe(envio.idPedido);
        const despachado = pedido ? Number(pedido.cantidadKg) : 0;
        const suma = (v) => (v.cantidadRecibidaKg || 0) + (v.mermaKg || 0) + (v.rechazoKg || 0);
        const liquidacion = await window.Ui.panel({
            titulo: 'Registrar la entrega',
            texto: 'Se despacharon ' + window.Ui.kilos(despachado) + '. Cuenta cuánto llegó bien, cuánto se perdió en el '
                + 'camino y cuánto rechazó el comprador. Entre los tres deben sumar lo despachado.',
            textoEnviar: 'Registrar entrega',
            campos: [
                {id: 'cantidadRecibidaKg', etiqueta: 'Llegó bien (kg)', tipo: 'numero', requerido: true, permiteCero: true},
                {id: 'mermaKg', etiqueta: 'Merma en el camino (kg)', tipo: 'numero', requerido: true, permiteCero: true},
                {id: 'rechazoKg', etiqueta: 'Rechazado por el comprador (kg)', tipo: 'numero', requerido: true,
                    permiteCero: true,
                    validar: (_, todos) => Math.abs(suma(todos) - despachado) > TOLERANCIA_KG
                        ? 'Entre lo que llegó, la merma y el rechazo deben sumar ' + window.Ui.kilos(despachado)
                        + ' (ahora suman ' + window.Ui.kilos(Math.round(suma(todos) * 1000) / 1000) + ').' : null}
            ],
            nota: (v) => {
                if (v.cantidadRecibidaKg === null && v.mermaKg === null && v.rechazoKg === null) {
                    return '';
                }
                const resto = Math.round((despachado - suma(v)) * 1000) / 1000;
                if (Math.abs(resto) <= TOLERANCIA_KG) {
                    return 'Todo cuadra: ' + window.Ui.kilos(despachado) + ' explicados.';
                }
                return resto > 0 ? 'Faltan ' + window.Ui.kilos(resto) + ' por explicar.'
                    : 'Te pasas por ' + window.Ui.kilos(-resto) + '.';
            },
            enviar: (v) => window.Api.pedir('/api/envios/' + envio.idEnvio + '/entrega', {metodo: 'POST', cuerpo: {
                cantidadRecibidaKg: v.cantidadRecibidaKg || 0, mermaKg: v.mermaKg || 0, rechazoKg: v.rechazoKg || 0}})
        });
        if (liquidacion) {
            cargar();
            await mostrarLiquidacion(liquidacion);
        }
    }

    async function verLiquidacion(envio) {
        try {
            await mostrarLiquidacion(await window.Api.pedir('/api/lotes/' + envio.idLote + '/liquidacion'));
        } catch (error) {
            window.Ui.aviso(error.message, 'error');
        }
    }

    // Cada enlace se genera aparte y solo cuando se pide: abrir esta ventana no corta a nadie. AgroRuta no guarda los
    // enlaces (solo su huella), así que solo se pueden mostrar al generarlos.
    function seccionEnlace(envio, tipo, titulo, explicacion, descripcionQr) {
        const caja = el('section', 'bloque');
        caja.append(el('h3', null, titulo), el('p', 'suave', explicacion));
        const resultado = el('div');
        const boton = window.Ui.boton('Generar ' + titulo.toLowerCase(), 'boton-secundario', async () => {
            boton.disabled = true;
            try {
                const r = await window.Api.pedir('/api/envios/' + envio.idEnvio + '/enlaces?tipo=' + tipo,
                    {metodo: 'POST'});
                const url = window.location.origin + (tipo === 'CONDUCTOR' ? r.rutaConductor : r.rutaComprador);
                resultado.replaceChildren(window.Ui.campoCopiable('Enlace nuevo', url),
                    window.Ui.codigoQr(url, descripcionQr),
                    el('p', 'suave', 'Vence el ' + window.Ui.fecha(r.fechaExpiracion)),
                    el('p', 'suave', 'Quien tenga el enlace puede verlo, sin contraseña: envíalo solo a quien corresponde.'));
                boton.textContent = 'Generar otro (el de arriba dejará de funcionar)';
            } catch (error) {
                window.Ui.aviso(error.message, 'error');
            } finally {
                boton.disabled = false;
            }
        });
        caja.append(boton, resultado);
        return caja;
    }

    async function verEnlaces(envio) {
        const contenido = el('div');
        if (envio.estadoEnvio !== 'ENTREGADO') {
            contenido.appendChild(seccionEnlace(envio, 'CONDUCTOR', 'Enlace del conductor',
                'Para que el conductor comparta su ubicación. Si ya le diste uno, ese deja de funcionar y tendrá '
                + 'que abrir el nuevo.',
                'Pídele al conductor que escanee este código con la cámara de su celular'));
        }
        contenido.appendChild(seccionEnlace(envio, 'COMPRADOR', 'Enlace del comprador',
            'Para que el comprador siga su pedido en el mapa. Si ya le diste uno, ese deja de funcionar. '
            + 'Generarlo no afecta al conductor.',
            'Código para el comprador, si prefiere escanearlo'));
        await window.Ui.detalle({titulo: 'Enlaces del envío ' + envio.idEnvio, contenido, ancho: true});
    }

    async function verMapa(envio) {
        const contenido = el('div');
        const resumen = el('p', 'bloque');
        const mapa = el('div', 'mapa');
        const alertas = el('div', 'bloque');
        contenido.append(resumen, mapa, alertas);
        let temporizador = null;
        let vista = null;
        const marcas = {destino: null, vehiculo: null, trazo: null};

        function iconos(clase, tamano) {
            return L.divIcon({className: '', html: '<div class="' + clase + '"></div>', iconSize: [tamano, tamano]});
        }

        async function actualizar() {
            try {
                const [s, listaAlertas] = await Promise.all([
                    window.Api.pedir('/api/envios/' + envio.idEnvio + '/seguimiento'),
                    window.Api.pedir('/api/envios/' + envio.idEnvio + '/alertas')]);
                const eta = s.etaMinutos === null || s.etaMinutos === undefined ? 'sin calcular'
                    : (s.etaMinutos < 1 ? 'menos de 1 minuto' : s.etaMinutos + ' min');
                resumen.textContent = 'Hacia ' + (s.nombreDestino || '—') + (s.ciudadDestino ? ' (' + s.ciudadDestino + ')' : '')
                    + ' · Llegada estimada: ' + eta + (s.ultimaPosicion ? ' · Última posición: '
                    + window.Ui.fecha(s.ultimaPosicion.fechaPosicion) : ' · Aún no hay posición del conductor');
                const puntos = [];
                if (s.latitudDestino !== null && s.latitudDestino !== undefined) {
                    const punto = [s.latitudDestino, s.longitudDestino];
                    puntos.push(punto);
                    if (!marcas.destino) {
                        marcas.destino = L.marker(punto, {icon: iconos('marcador-destino', 16)}).addTo(vista);
                    }
                }
                if (s.ultimaPosicion) {
                    const punto = [s.ultimaPosicion.latitud, s.ultimaPosicion.longitud];
                    puntos.push(punto);
                    if (!marcas.vehiculo) {
                        marcas.vehiculo = L.marker(punto, {icon: iconos('marcador-vehiculo', 18)}).addTo(vista);
                    } else {
                        marcas.vehiculo.setLatLng(punto);
                    }
                }
                if (marcas.trazo) {
                    marcas.trazo.remove();
                }
                if ((s.trazoReciente || []).length > 1) {
                    marcas.trazo = L.polyline(s.trazoReciente.map((p) => [p.latitud, p.longitud]),
                        {color: '#1E7F4B', weight: 4}).addTo(vista);
                }
                if (puntos.length > 0 && !vista.encuadrado) {
                    vista.fitBounds(puntos, {padding: [40, 40], maxZoom: 14});
                    vista.encuadrado = true;
                }
                alertas.replaceChildren();
                if (listaAlertas.length > 0) {
                    alertas.appendChild(el('h3', null, 'Alertas'));
                    for (const a of listaAlertas) {
                        alertas.appendChild(el('p', 'aviso aviso--atencion', (ALERTAS[a.tipoAlerta] || a.tipoAlerta) + ' · '
                            + window.Ui.fecha(a.fechaAlerta)));
                    }
                }
            } catch (error) {
                resumen.textContent = error.message;
            }
        }

        const cerrado = window.Ui.detalle({titulo: 'Envío ' + envio.idEnvio + ' en el mapa', contenido, ancho: true});
        vista = L.map(mapa).setView([-12.05, -77.04], 6);
        L.tileLayer('https://tile.openstreetmap.org/{z}/{x}/{y}.png', {maxZoom: 18,
            attribution: '&copy; OpenStreetMap'}).addTo(vista);
        setTimeout(() => vista.invalidateSize(), 300);
        await actualizar();
        if (envio.estadoEnvio === 'EN_RUTA') {
            temporizador = setInterval(actualizar, REFRESCO_MAPA_MS);
        }
        await cerrado;
        clearInterval(temporizador);
        vista.remove();
    }

    function acciones(e) {
        const botones = [];
        if (e.estadoEnvio === 'PROGRAMADO') {
            botones.push(window.Ui.boton('Iniciar ruta', '', () => iniciarRuta(e)));
        }
        if (e.estadoEnvio === 'EN_RUTA') {
            botones.push(window.Ui.boton('Registrar entrega', '', () => registrarEntrega(e)));
            botones.push(window.Ui.boton('Mapa', 'boton-secundario', () => verMapa(e)));
        }
        if (e.estadoEnvio === 'ENTREGADO') {
            botones.push(window.Ui.boton('Ver liquidación', '', () => verLiquidacion(e)));
        }
        botones.push(window.Ui.boton('Enlaces', 'boton-secundario', () => verEnlaces(e)));
        return botones;
    }

    const barra = window.Ui.barraFiltros($('barra-filtros'), [
        {id: 'texto', tipo: 'busqueda', etiqueta: 'Buscar', marcador: 'N.º, comprador, destino o placa…'},
        {id: 'destino', tipo: 'select', etiqueta: 'Destino'},
        {id: 'comprador', tipo: 'select', etiqueta: 'Comprador'},
        {id: 'vehiculo', tipo: 'select', etiqueta: 'Vehículo'},
        {id: 'desde', tipo: 'fecha', etiqueta: 'Desde'},
        {id: 'hasta', tipo: 'fecha', etiqueta: 'Hasta'}
    ], pintar);

    const destinoDeEnvio = (e) => (pedidoDe(e.idPedido) || {}).idDestinoVenta;
    const compradorDeEnvio = (e) => (pedidoDe(e.idPedido) || {}).idComprador;
    const fechaDeEnvio = (e) => e.fechaEntrega || e.fechaSalida || e.fechaProgramada;

    function pintar() {
        const f = barra.valores();
        const visibles = envios.filter((e) => (!filtro || e.estadoEnvio === filtro)
            && window.Ui.coincide(f.texto, e.idEnvio, e.idPedido, compradorDe(compradorDeEnvio(e)),
                destinoDe(destinoDeEnvio(e)), placaDe(e.idVehiculo))
            && (!f.destino || String(destinoDeEnvio(e)) === f.destino)
            && (!f.comprador || String(compradorDeEnvio(e)) === f.comprador)
            && (!f.vehiculo || String(e.idVehiculo) === f.vehiculo)
            && window.Ui.entreFechas(fechaDeEnvio(e), f.desde, f.hasta));
        barra.contar(visibles.length, envios.length);
        const filtrando = filtro || Object.values(f).some(Boolean);
        window.Ui.tabla($('lista'), [
            {titulo: 'N.º', valor: (e) => e.idEnvio},
            {titulo: 'Pedido', clase: 'largo', valor: resumenPedido},
            {titulo: 'Destino', clase: 'largo', valor: (e) => destinoDe(destinoDeEnvio(e))},
            {titulo: 'Vehículo', valor: (e) => placaDe(e.idVehiculo)},
            {titulo: 'Estado', valor: (e) => window.Ui.etiqueta(ESTADOS[e.estadoEnvio][0], ESTADOS[e.estadoEnvio][1])},
            {titulo: 'Novedad', valor: novedad}
        ], visibles, {
            vacio: {titulo: filtrando ? 'Ningún envío coincide' : 'Aún no hay envíos',
                texto: filtrando ? 'Cambia el filtro o pulsa «Limpiar».' : 'Despacha un pedido para empezar.'},
            acciones
        });
    }

    function pintarFiltros() {
        const caja = $('filtros');
        caja.replaceChildren();
        for (const [valor, texto] of FILTROS) {
            const b = el('button', 'filtro', texto);
            b.type = 'button';
            b.setAttribute('aria-pressed', valor === filtro ? 'true' : 'false');
            b.addEventListener('click', () => { filtro = valor; pintarFiltros(); pintar(); });
            caja.appendChild(b);
        }
    }

    const lista = {
        async cargar() {
            envios = await window.Ui.cargarTodo('/api/envios');
            barra.opciones('destino', window.Ui.opcionesUnicas(destinos, (d) => d.idDestinoVenta, (d) => d.nombreDestino));
            barra.opciones('comprador', window.Ui.opcionesUnicas(compradores, (c) => c.idComprador,
                (c) => c.nombreComprador));
            barra.opciones('vehiculo', window.Ui.opcionesUnicas(vehiculos, (v) => v.idVehiculo, (v) => v.placaVehiculo));
            pintar();
        }
    };

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            [pedidos, compradores, destinos, vehiculos] = await Promise.all([
                window.Ui.cargarTodo('/api/pedidos'), window.Api.pedir('/api/compradores'),
                window.Api.pedir('/api/destinos'), window.Api.pedir('/api/vehiculos')]);
            await lista.cargar();
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    pintarFiltros();
    $('accion-principal').classList.remove('oculto');
    $('accion-principal').addEventListener('click', () => despachar().catch((e) => window.Ui.aviso(e.message, 'error')));
    window.Ui.alReintentar(cargar);
    cargar();
})();
