'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'lotes', titulo: () => 'Lotes'})) {
        return;
    }

    const $ = (id) => document.getElementById(id);
    const el = window.Ui.el;
    const ESTADOS = {
        REGISTRADO: ['Disponible', ''], RESERVADO: ['Reservado', 'espera'], DESPACHADO: ['En camino', 'espera'],
        ENTREGADO: ['Entregado', ''], LIQUIDADO: ['Liquidado', 'neutro']
    };
    const EVENTOS = {CANCELACION: 'Cancelación', CORRECCION: 'Entrega corregida', REGISTRO: 'Registrado', RESERVA: 'Reservado por un pedido', DESPACHO: 'Despachado',
        ENTREGA: 'Entregado', LIQUIDACION: 'Liquidado'};
    const MOTIVOS = {SIN_PRECIO: 'falta el precio por kilo', SIN_TARIFA: 'falta el flete'};

    function hoy() {
        return new Date().toLocaleDateString('en-CA');
    }

    async function registrar() {
        const creado = await window.Ui.panel({
            titulo: 'Registrar lote',
            textoEnviar: 'Registrar lote',
            campos: [
                {id: 'productoLote', etiqueta: 'Producto', tipo: 'texto', requerido: true, marcador: 'Palta Hass'},
                {id: 'cantidadKg', etiqueta: 'Cantidad cosechada (kg)', tipo: 'numero', requerido: true},
                {id: 'fechaCosecha', etiqueta: 'Fecha de cosecha', tipo: 'fecha', requerido: true, maximoFecha: hoy(),
                    valor: hoy(), validar: (v) => v > hoy() ? 'La fecha de cosecha no puede ser futura.' : null},
                {id: 'chacraOrigen', etiqueta: 'Chacra de origen', tipo: 'texto', requerido: true}
            ],
            enviar: (v) => window.Api.pedir('/api/lotes', {metodo: 'POST', cuerpo: {
                productoLote: v.productoLote, cantidadKg: v.cantidadKg, fechaCosecha: v.fechaCosecha,
                chacraOrigen: v.chacraOrigen}})
        });
        if (creado) {
            window.Ui.aviso('Lote registrado');
            lista.cargar().catch((error) => window.Ui.errorDePagina(error.message));
        }
    }

    function bloqueHistorial(eventos) {
        const bloque = el('section', 'bloque');
        bloque.appendChild(el('h3', null, 'Historial'));
        const ul = el('ul', 'linea-tiempo');
        for (const e of eventos) {
            const li = el('li');
            li.append(el('strong', null, EVENTOS[e.tipoEvento] || e.tipoEvento),
                el('div', 'suave', e.descripcionEvento + ' · ' + e.usuarioEvento + ' · ' + window.Ui.fecha(e.fechaEvento)));
            ul.appendChild(li);
        }
        bloque.appendChild(ul);
        return bloque;
    }

    function bloqueCotizacion(cotizacion, error) {
        const bloque = el('section', 'bloque');
        bloque.appendChild(el('h3', null, '¿A dónde conviene enviarlo?'));
        if (error) {
            bloque.appendChild(el('p', 'suave', error));
            return bloque;
        }
        if (cotizacion.opciones.length === 0) {
            const p = el('p', 'suave', 'Ningún destino tiene precio y flete cargados para este producto. ');
            const enlace = el('a', null, 'Cárgalos en Destinos');
            enlace.href = '/destinos.html';
            p.appendChild(enlace);
            bloque.appendChild(p);
        } else {
            const caja = el('div');
            // Para el Operador la API no manda flete ni ganancia: solo ve a dónde conviene enviarlo.
            const conDinero = cotizacion.opciones[0].gananciaNeta !== undefined;
            const columnas = [
                {titulo: 'Destino', clase: 'largo', valor: (o) => o.nombreDestino},
                {titulo: 'Precio/kg', valor: (o) => window.Ui.dinero(o.precioEsperadoPorKg)}
            ];
            if (conDinero) {
                columnas.push({titulo: 'Flete/kg', valor: (o) => window.Ui.dinero(o.costoFletePorKg)},
                    {titulo: 'Ganancia neta', valor: (o) => window.Ui.dinero(o.gananciaNeta)});
            }
            window.Ui.tabla(caja, columnas, cotizacion.opciones, {filaDestacada: (o) => o.conviene});
            bloque.appendChild(caja);
            bloque.appendChild(el('p', 'suave', conDinero
                ? 'La fila en verde es la que más te deja después del flete.'
                : 'La fila en verde es el destino que más conviene.'));
        }
        for (const omitido of cotizacion.destinosSinDatos || []) {
            bloque.appendChild(el('p', 'suave', omitido.nombreDestino + ': ' + (MOTIVOS[omitido.motivo] || omitido.motivo)));
        }
        return bloque;
    }

    async function verDetalle(lote) {
        const contenido = el('div');
        contenido.appendChild(el('p', 'suave', 'Cargando…'));
        const abierto = window.Ui.detalle({titulo: lote.productoLote + ' · ' + window.Ui.kilos(lote.cantidadKg)
                + ' · quedan ' + window.Ui.kilos(lote.kgDisponibles),
            contenido, ancho: true});
        try {
            const [eventos, cotizacion] = await Promise.all([
                window.Api.pedir('/api/lotes/' + lote.idLote + '/historial'),
                window.Api.pedir('/api/lotes/' + lote.idLote + '/cotizacion').catch((e) => ({error: e.message}))
            ]);
            contenido.replaceChildren(
                cotizacion.error ? bloqueCotizacion(null, cotizacion.error) : bloqueCotizacion(cotizacion),
                bloqueHistorial(eventos));
        } catch (error) {
            contenido.replaceChildren(el('p', 'aviso aviso--error', error.message));
        }
        await abierto;
    }

    let lotes = [];
    const filtros = window.Ui.barraFiltros($('barra-filtros'), [
        {id: 'texto', tipo: 'busqueda', etiqueta: 'Buscar', marcador: 'Producto o chacra…'},
        {id: 'estado', tipo: 'select', etiqueta: 'Estado',
            opciones: Object.entries(ESTADOS).map(([valor, [texto]]) => [valor, texto])},
        {id: 'producto', tipo: 'select', etiqueta: 'Producto'},
        {id: 'chacra', tipo: 'select', etiqueta: 'Chacra', todos: 'Todas'},
        {id: 'desde', tipo: 'fecha', etiqueta: 'Cosecha desde'},
        {id: 'hasta', tipo: 'fecha', etiqueta: 'Cosecha hasta'}
    ], pintar);

    function pintar() {
        const f = filtros.valores();
        const visibles = lotes.filter((l) => window.Ui.coincide(f.texto, l.productoLote, l.chacraOrigen)
            && (!f.estado || l.estadoLote === f.estado)
            && (!f.producto || l.productoLote === f.producto)
            && (!f.chacra || l.chacraOrigen === f.chacra)
            && window.Ui.entreFechas(l.fechaCosecha, f.desde, f.hasta));
        filtros.contar(visibles.length, lotes.length);
        const filtrando = Object.values(f).some(Boolean);
        window.Ui.tabla($('lista'), [
            {titulo: 'Producto', clase: 'largo', valor: (l) => l.productoLote},
            {titulo: 'Cantidad', clase: 'numero', valor: (l) => window.Ui.kilos(l.cantidadKg)},
            {titulo: 'Disponible', clase: 'numero', valor: (l) => window.Ui.kilos(l.kgDisponibles)},
            {titulo: 'Cosecha', valor: (l) => window.Ui.dia(l.fechaCosecha)},
            {titulo: 'Chacra', clase: 'largo', valor: (l) => l.chacraOrigen},
            {titulo: 'Estado', valor: (l) => window.Ui.etiqueta((ESTADOS[l.estadoLote] || [l.estadoLote])[0],
                (ESTADOS[l.estadoLote] || [])[1])}
        ], visibles, {
            vacio: filtrando
                ? {titulo: 'Ningún lote coincide', texto: 'Cambia la búsqueda o pulsa «Limpiar».'}
                : {titulo: 'Aún no hay lotes', texto: 'Registra tu primera cosecha para empezar a calcular ganancias.'},
            acciones: (l) => [window.Ui.boton('Ver detalle', 'boton-secundario', () => verDetalle(l))]
        });
    }

    const lista = {
        async cargar() {
            lotes = await window.Ui.cargarTodo('/api/lotes');
            filtros.opciones('producto', window.Ui.opcionesUnicas(lotes, (l) => l.productoLote));
            filtros.opciones('chacra', window.Ui.opcionesUnicas(lotes, (l) => l.chacraOrigen));
            pintar();
        }
    };

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            await lista.cargar();
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    $('accion-principal').classList.remove('oculto');
    $('accion-principal').addEventListener('click', registrar);
    window.Ui.alReintentar(cargar);
    cargar();
})();
