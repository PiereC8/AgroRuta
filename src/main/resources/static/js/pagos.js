'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'pagos', titulo: () => 'Pagos'})) {
        return;
    }

    const $ = (id) => document.getElementById(id);
    const el = window.Ui.el;
    const ESTADOS_PAGO = {APROBADO: ['Aprobado', ''], PENDIENTE: ['Pendiente', 'espera'], RECHAZADO: ['Rechazado', 'error']};
    const ESTADOS_COMPROBANTE = {
        PENDIENTE: ['Pendiente: se está enviando a SUNAT', 'espera'], ACEPTADO: ['Aceptado por SUNAT', ''],
        OBSERVADO: ['Emitido con observaciones', 'espera'], RECHAZADO: ['No se pudo emitir', 'error']
    };

    let pedidos = [];
    let compradores = [];

    const pedidoDe = (id) => pedidos.find((p) => p.idPedido === id);
    const compradorDe = (idPedido) => {
        const p = pedidoDe(idPedido);
        const c = p && compradores.find((x) => x.idComprador === p.idComprador);
        return c ? c.nombreComprador : '—';
    };

    function medioDePago(p) {
        if (!p.marcaTarjeta) {
            return '—';
        }
        return p.marcaTarjeta + (p.ultimosCuatro ? ' ····' + p.ultimosCuatro : '');
    }

    function enlaceHttps(texto, url) {
        if (!url || !/^https:\/\//.test(url)) {
            return null;
        }
        const a = el('a', null, texto);
        a.href = url;
        a.target = '_blank';
        a.rel = 'noopener noreferrer';
        return a;
    }

    async function verComprobante(pago) {
        const contenido = el('div');
        contenido.appendChild(el('p', 'suave', 'Cargando…'));
        const cerrado = window.Ui.detalle({titulo: 'Comprobante del pago ' + pago.idPago, contenido});

        async function pintar(comprobante) {
            const [estado, tono] = ESTADOS_COMPROBANTE[comprobante.estadoComprobante]
                || [comprobante.estadoComprobante, 'neutro'];
            contenido.replaceChildren();
            const cifras = el('div', 'cifras-liquidacion');
            const tipo = el('div', 'tarjeta');
            tipo.append(el('div', 'tarjeta__titulo', comprobante.tipoComprobante === 'FACTURA' ? 'Factura' : 'Boleta'),
                el('div', 'cifra', comprobante.numero));
            const situacion = el('div', 'tarjeta');
            situacion.append(el('div', 'tarjeta__titulo', 'Estado'), window.Ui.etiqueta(estado, tono));
            cifras.append(tipo, situacion);
            contenido.appendChild(cifras);
            if (comprobante.mensajeSunat) {
                contenido.appendChild(el('p', 'suave', comprobante.mensajeSunat));
            }
            const enlaces = el('p');
            for (const a of [enlaceHttps('Ver PDF', comprobante.enlacePdf), enlaceHttps('Ver XML', comprobante.enlaceXml)]) {
                if (a) {
                    enlaces.append(a, document.createTextNode('   '));
                }
            }
            contenido.appendChild(enlaces);
            if (comprobante.estadoComprobante === 'PENDIENTE') {
                contenido.appendChild(el('p', 'suave', 'Intentos: ' + comprobante.intentos
                    + '. Se vuelve a intentar solo cada 5 minutos; también puedes hacerlo ahora.'));
                const fila = el('div', 'fila-acciones');
                fila.append(
                    window.Ui.boton('Reintentar el envío', '', () => accionar('reintentar', comprobante.idComprobante)),
                    window.Ui.boton('Consultar el estado', 'boton-secundario',
                        () => accionar('consultar', comprobante.idComprobante)));
                contenido.appendChild(fila);
            }
        }

        async function accionar(accion, id) {
            try {
                await pintar(await window.Api.pedir('/api/comprobantes/' + id + '/' + accion, {metodo: 'POST'}));
            } catch (error) {
                window.Ui.aviso(error.message, 'error');
            }
        }

        try {
            await pintar(await window.Api.pedir('/api/pagos/' + pago.idPago + '/comprobante'));
        } catch (error) {
            contenido.replaceChildren(el('p', error.estado === 404 ? 'suave' : 'aviso aviso--error',
                error.estado === 404 ? 'Este pago todavía no tiene comprobante.' : error.message));
        }
        await cerrado;
    }

    async function enlaceDePago(liquidacion) {
        try {
            const enlaces = await window.Api.pedir('/api/envios/' + liquidacion.idEnvio + '/enlaces', {metodo: 'POST'});
            const contenido = el('div');
            const caja = window.Ui.campoCopiable('Enlace de pago para el comprador',
                window.location.origin + '/pago.html#t=' + enlaces.tokenComprador);
            contenido.append(caja, el('p', 'suave', 'Con este enlace el comprador paga con tarjeta, sin contraseña. Vence el '
                + window.Ui.fecha(enlaces.fechaExpiracion) + '.'));
            await window.Ui.detalle({titulo: 'Cobrar el pedido ' + liquidacion.idPedido, contenido, ancho: true});
        } catch (error) {
            window.Ui.aviso(error.message, 'error');
        }
    }

    function pintarPorCobrar(liquidaciones, pagos) {
        const aprobadas = new Set(pagos.filter((p) => p.estadoPago === 'APROBADO').map((p) => p.idLiquidacion));
        const pendientes = liquidaciones.filter((l) => !aprobadas.has(l.idLiquidacion));
        const ultimoDe = (l) => pagos.find((p) => p.idLiquidacion === l.idLiquidacion);
        window.Ui.tabla($('por-cobrar'), [
            {titulo: 'Pedido', clase: 'largo', valor: (l) => l.idPedido},
            {titulo: 'Comprador', clase: 'largo', valor: (l) => compradorDe(l.idPedido)},
            {titulo: 'Entregado', valor: (l) => window.Ui.fecha(l.fechaEntrega)},
            {titulo: 'Importe a cobrar', valor: (l) => window.Ui.dinero(l.ingresoReal)},
            {titulo: 'Situación', valor: (l) => {
                const u = ultimoDe(l);
                return u ? window.Ui.etiqueta(ESTADOS_PAGO[u.estadoPago][0], ESTADOS_PAGO[u.estadoPago][1])
                    : window.Ui.etiqueta('Sin pagar', 'espera');
            }}
        ], pendientes, {
            vacio: {titulo: 'No hay nada por cobrar', texto: 'Todo lo entregado ya está pagado.'},
            acciones: (l) => [window.Ui.boton('Enlace de pago', '', () => enlaceDePago(l))]
        });
    }

    const lista = window.Ui.listaPaginada({
        ruta: '/api/pagos',
        mas: $('mas'),
        alCargar: (pagos) => window.Ui.tabla($('lista'), [
            {titulo: 'N.º', valor: (p) => p.idPago},
            {titulo: 'Pedido', clase: 'largo', valor: (p) => p.idPedido},
            {titulo: 'Comprador', clase: 'largo', valor: (p) => compradorDe(p.idPedido)},
            {titulo: 'Monto', valor: (p) => window.Ui.dinero(p.monto)},
            {titulo: 'Estado', valor: (p) => window.Ui.etiqueta((ESTADOS_PAGO[p.estadoPago] || [p.estadoPago])[0],
                (ESTADOS_PAGO[p.estadoPago] || [])[1])},
            {titulo: 'Medio de pago', valor: medioDePago},
            {titulo: 'Correo', clase: 'largo', valor: (p) => p.emailPagador},
            {titulo: 'Fecha', valor: (p) => window.Ui.fecha(p.fechaActualizacion)}
        ], pagos, {
            vacio: {titulo: 'Aún no hay pagos', texto: 'Cuando un comprador pague con su enlace, aparecerá aquí.'},
            acciones: (p) => p.estadoPago === 'APROBADO'
                ? [window.Ui.boton('Ver comprobante', 'boton-secundario', () => verComprobante(p))] : []
        })
    });

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            let liquidaciones;
            let todosLosPagos;
            [pedidos, compradores, liquidaciones, todosLosPagos] = await Promise.all([
                window.Ui.cargarPagina('/api/pedidos', {tamano: 200}), window.Api.pedir('/api/compradores'),
                window.Ui.cargarPagina('/api/liquidaciones', {tamano: 200}),
                window.Ui.cargarPagina('/api/pagos', {tamano: 200})]);
            pintarPorCobrar(liquidaciones, todosLosPagos);
            await lista.cargar();
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    window.Ui.alReintentar(cargar);
    cargar();
})();
