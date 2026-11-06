'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'lotes', titulo: () => 'Lotes'})) {
        return;
    }

    const $ = (id) => document.getElementById(id);
    const el = window.Ui.el;
    const ESTADOS = {
        REGISTRADO: ['Disponible', ''], RESERVADO: ['Con pedido', 'espera'], DESPACHADO: ['En camino', 'espera'],
        ENTREGADO: ['Entregado', ''], LIQUIDADO: ['Liquidado', 'neutro']
    };
    const EVENTOS = {REGISTRO: 'Registrado', RESERVA: 'Reservado por un pedido', DESPACHO: 'Despachado',
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
            window.Ui.tabla(caja, [
                {titulo: 'Destino', clase: 'largo', valor: (o) => o.nombreDestino},
                {titulo: 'Precio/kg', valor: (o) => window.Ui.dinero(o.precioEsperadoPorKg)},
                {titulo: 'Flete/kg', valor: (o) => window.Ui.dinero(o.costoFletePorKg)},
                {titulo: 'Ganancia neta', valor: (o) => window.Ui.dinero(o.gananciaNeta)}
            ], cotizacion.opciones, {filaDestacada: (o) => o.conviene});
            bloque.appendChild(caja);
            bloque.appendChild(el('p', 'suave', 'La fila en verde es la que más te deja después del flete.'));
        }
        for (const omitido of cotizacion.destinosSinDatos || []) {
            bloque.appendChild(el('p', 'suave', omitido.nombreDestino + ': ' + (MOTIVOS[omitido.motivo] || omitido.motivo)));
        }
        return bloque;
    }

    async function verDetalle(lote) {
        const contenido = el('div');
        contenido.appendChild(el('p', 'suave', 'Cargando…'));
        const abierto = window.Ui.detalle({titulo: lote.productoLote + ' · ' + window.Ui.kilos(lote.cantidadKg),
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

    const lista = window.Ui.listaPaginada({
        ruta: '/api/lotes',
        mas: $('mas'),
        alCargar: (lotes) => window.Ui.tabla($('lista'), [
            {titulo: 'Producto', clase: 'largo', valor: (l) => l.productoLote},
            {titulo: 'Cantidad', valor: (l) => window.Ui.kilos(l.cantidadKg)},
            {titulo: 'Cosecha', valor: (l) => window.Ui.dia(l.fechaCosecha)},
            {titulo: 'Chacra', clase: 'largo', valor: (l) => l.chacraOrigen},
            {titulo: 'Estado', valor: (l) => window.Ui.etiqueta((ESTADOS[l.estadoLote] || [l.estadoLote])[0],
                (ESTADOS[l.estadoLote] || [])[1])}
        ], lotes, {
            vacio: {titulo: 'Aún no hay lotes', texto: 'Registra tu primera cosecha para empezar a calcular ganancias.'},
            acciones: (l) => [window.Ui.boton('Ver detalle', 'boton-secundario', () => verDetalle(l))]
        })
    });

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
