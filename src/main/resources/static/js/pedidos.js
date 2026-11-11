'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'pedidos', titulo: () => 'Pedidos'})) {
        return;
    }

    const $ = (id) => document.getElementById(id);
    const ESTADOS = {PENDIENTE: ['Pendiente de despacho', 'espera'], DESPACHADO: ['En camino', 'espera'],
        ENTREGADO: ['Entregado', ''], CANCELADO: ['Cancelado', 'neutro']};
    let compradores = [];
    let destinos = [];
    let lotes = [];

    const nombreComprador = (id) => (compradores.find((c) => c.idComprador === id) || {}).nombreComprador || 'Comprador ' + id;
    const nombreDestino = (id) => (destinos.find((d) => d.idDestinoVenta === id) || {}).nombreDestino || 'Destino ' + id;
    const nombreLote = (id) => {
        const l = lotes.find((x) => x.idLote === id);
        return l ? l.productoLote + ' · ' + l.chacraOrigen : 'Lote ' + id;
    };

    async function cargarAuxiliares() {
        [compradores, destinos, lotes] = await Promise.all([
            window.Api.pedir('/api/compradores'),
            window.Api.pedir('/api/destinos'),
            window.Ui.cargarPagina('/api/lotes', {tamano: 200})
        ]);
    }

    async function avisarFalta(titulo, texto) {
        await window.Ui.detalle({titulo, texto, contenido: window.Ui.el('span')});
    }

    async function registrar() {
        await cargarAuxiliares();
        const disponibles = lotes.filter((l) => l.estadoLote === 'REGISTRADO');
        if (compradores.length === 0) {
            return avisarFalta('Primero registra un comprador', 'Un pedido necesita saber quién compra.');
        }
        if (destinos.length === 0) {
            return avisarFalta('Primero registra un destino', 'Un pedido necesita saber a dónde va.');
        }
        if (disponibles.length === 0) {
            return avisarFalta('No hay lotes disponibles',
                'Todos tus lotes ya tienen pedido. Registra una nueva cosecha para crear otro pedido.');
        }
        const cotizaciones = new Map();
        let precioSugerido = null;

        async function sugerirPrecio(valores, entradas) {
            if (!valores.idLote || !valores.idDestinoVenta) {
                return;
            }
            let cotizacion = cotizaciones.get(valores.idLote);
            if (!cotizacion) {
                cotizacion = await window.Api.pedir('/api/lotes/' + valores.idLote + '/cotizacion').catch(() => null);
                cotizaciones.set(valores.idLote, cotizacion);
            }
            const opcion = cotizacion && cotizacion.opciones.find((o) => String(o.idDestinoVenta) === valores.idDestinoVenta);
            const campo = entradas.get('precioAcordadoPorKg').entrada;
            if (opcion && (campo.value.trim() === '' || campo.value === precioSugerido)) {
                precioSugerido = String(opcion.precioEsperadoPorKg);
                campo.value = precioSugerido;
                campo.dispatchEvent(new Event('input', {bubbles: true}));
            }
        }

        const creado = await window.Ui.panel({
            titulo: 'Registrar pedido',
            textoEnviar: 'Registrar pedido',
            campos: [
                {id: 'idComprador', etiqueta: 'Comprador', tipo: 'select', requerido: true,
                    opciones: compradores.map((c) => ({valor: c.idComprador, texto: c.nombreComprador}))},
                {id: 'idLote', etiqueta: 'Lote', tipo: 'select', requerido: true,
                    opciones: disponibles.map((l) => ({valor: l.idLote,
                        texto: l.productoLote + ' · ' + window.Ui.kilos(l.cantidadKg) + ' · ' + l.chacraOrigen}))},
                {id: 'idDestinoVenta', etiqueta: 'Destino', tipo: 'select', requerido: true,
                    opciones: destinos.map((d) => ({valor: d.idDestinoVenta, texto: d.nombreDestino + ' (' + d.ciudadDestino + ')'}))},
                {id: 'precioAcordadoPorKg', etiqueta: 'Precio acordado por kilo (S/)', tipo: 'numero', requerido: true,
                    ayuda: 'Si el destino tiene precio cargado, lo sugerimos aquí. Puedes cambiarlo.'}
            ],
            alCambiar: sugerirPrecio,
            nota: (v) => {
                const lote = disponibles.find((l) => String(l.idLote) === v.idLote);
                return lote && v.precioAcordadoPorKg
                    ? 'Ingreso acordado: ' + window.Ui.dinero(lote.cantidadKg * v.precioAcordadoPorKg) : '';
            },
            enviar: (v) => window.Api.pedir('/api/pedidos', {metodo: 'POST', cuerpo: {
                idComprador: Number(v.idComprador), idLote: Number(v.idLote), idDestinoVenta: Number(v.idDestinoVenta),
                precioAcordadoPorKg: v.precioAcordadoPorKg}})
        });
        if (creado) {
            window.Ui.aviso('Pedido registrado');
            cargar();
        }
    }

    // Solo un pedido pendiente (sin envío en curso); el lote queda libre para otro pedido.
    async function cancelar(pedido) {
        const acepto = await window.Ui.confirmar({
            titulo: 'Cancelar el pedido ' + pedido.idPedido,
            texto: 'El pedido de ' + nombreComprador(pedido.idComprador) + ' queda cancelado y el lote '
                + nombreLote(pedido.idLote) + ' vuelve a estar disponible para otro pedido. '
                + 'Si ya tenía un envío, cancela primero el envío en la sección Envíos.',
            textoAceptar: 'Cancelar pedido',
            enviar: () => window.Api.pedir('/api/pedidos/' + pedido.idPedido + '/cancelar', {metodo: 'POST'})
        });
        if (acepto) {
            window.Ui.aviso('Pedido cancelado: el lote quedó disponible');
            cargar();
        }
    }

    const lista = window.Ui.listaPaginada({
        ruta: '/api/pedidos',
        mas: $('mas'),
        alCargar: (pedidos) => window.Ui.tabla($('lista'), [
            {titulo: 'N.º', valor: (p) => p.idPedido},
            {titulo: 'Comprador', clase: 'largo', valor: (p) => nombreComprador(p.idComprador)},
            {titulo: 'Lote', clase: 'largo', valor: (p) => nombreLote(p.idLote)},
            {titulo: 'Destino', clase: 'largo', valor: (p) => nombreDestino(p.idDestinoVenta)},
            {titulo: 'Cantidad', valor: (p) => window.Ui.kilos(p.cantidadKg)},
            {titulo: 'Precio/kg', valor: (p) => window.Ui.dinero(p.precioAcordadoPorKg)},
            {titulo: 'Ingreso acordado', valor: (p) => window.Ui.dinero(p.ingresoAcordado)},
            {titulo: 'Estado', valor: (p) => window.Ui.etiqueta((ESTADOS[p.estadoPedido] || [p.estadoPedido])[0],
                (ESTADOS[p.estadoPedido] || [])[1])}
        ], pedidos, {
            acciones: (p) => p.estadoPedido === 'PENDIENTE'
                ? [window.Ui.boton('Cancelar pedido', 'boton-secundario', () => cancelar(p))] : [],
            vacio: {titulo: 'Aún no hay pedidos',
                texto: 'Cuando un comprador te pida un lote, regístralo aquí para despacharlo.'}
        })
    });

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            await cargarAuxiliares();
            await lista.cargar();
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    $('accion-principal').classList.remove('oculto');
    $('accion-principal').addEventListener('click', () => registrar().catch((e) => window.Ui.aviso(e.message, 'error')));
    window.Ui.alReintentar(cargar);
    cargar();
})();
