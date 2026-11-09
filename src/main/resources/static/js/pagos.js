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
    const compradorDelPedido = (idPedido) => {
        const p = pedidoDe(idPedido);
        return p && compradores.find((x) => x.idComprador === p.idComprador);
    };
    const compradorDe = (idPedido) => (compradorDelPedido(idPedido) || {}).nombreComprador || '—';

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

    /** El PDF lo arma AgroRuta (el del proveedor suma el IGV a una línea exonerada). */
    async function descargarPdf(idPago, boton) {
        boton.disabled = true;
        try {
            const token = sessionStorage.getItem('agroruta.token');
            const respuesta = await fetch('/api/pagos/' + idPago + '/comprobante.pdf',
                {headers: {'Authorization': 'Bearer ' + token}, cache: 'no-store'});
            if (!respuesta.ok) {
                const datos = await respuesta.json().catch(() => null);
                throw new Error((datos && datos.error) || 'No se pudo generar el PDF. Inténtalo de nuevo.');
            }
            guardar(await respuesta.blob(), respuesta.headers.get('Content-Disposition'), 'comprobante.pdf');
        } catch (error) {
            window.Ui.aviso(error.message, 'error');
        } finally {
            boton.disabled = false;
        }
    }

    function guardar(contenido, disposicion, nombrePorDefecto) {
        const nombre = /filename="?([^";]+)"?/.exec(disposicion || '');
        const enlace = document.createElement('a');
        const url = URL.createObjectURL(contenido);
        enlace.href = url;
        enlace.download = nombre ? nombre[1] : nombrePorDefecto;
        document.body.appendChild(enlace);
        enlace.click();
        enlace.remove();
        setTimeout(() => URL.revokeObjectURL(url), 1000);
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
            if (comprobante.estadoComprobante === 'ACEPTADO' || comprobante.estadoComprobante === 'OBSERVADO') {
                const pdf = window.Ui.boton('Descargar PDF', 'boton-secundario', () => descargarPdf(pago.idPago, pdf));
                enlaces.append(pdf, document.createTextNode('   '));
            }
            const xml = enlaceHttps('Ver XML enviado a SUNAT', comprobante.enlaceXml);
            if (xml) {
                enlaces.append(xml);
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
            const url = window.location.origin + '/pago.html#t=' + enlaces.tokenComprador;
            const contenido = el('div');
            const botones = el('div', 'fila-acciones bloque');
            const abrir = el('a', 'boton', 'Abrir la página de pago');
            abrir.href = url;
            abrir.target = '_blank';
            abrir.rel = 'noopener';
            botones.appendChild(abrir);
            const comprador = compradorDelPedido(liquidacion.idPedido);
            const telefono = ((comprador && comprador.telefonoComprador) || '').replace(/\D/g, '');
            if (telefono.length >= 9) {
                const whatsapp = el('a', 'boton boton-secundario', 'Enviar por WhatsApp');
                const numero = telefono.length === 9 ? '51' + telefono : telefono;
                whatsapp.href = 'https://wa.me/' + numero + '?text=' + encodeURIComponent('Hola, este es el enlace '
                    + 'para pagar el pedido ' + liquidacion.idPedido + ' (' + window.Ui.dinero(liquidacion.ingresoReal)
                    + '): ' + url);
                whatsapp.target = '_blank';
                whatsapp.rel = 'noopener noreferrer';
                botones.appendChild(whatsapp);
            }
            contenido.append(botones, window.Ui.campoCopiable('O copia el enlace y envíaselo al comprador', url),
                el('p', 'suave', 'Con este enlace el comprador paga con tarjeta, PayPal o PagoEfectivo, sin contraseña. '
                    + 'Vence el ' + window.Ui.fecha(enlaces.fechaExpiracion) + '.'));
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

    let pagos = [];
    const MEDIOS = {visa: 'Visa', mastercard: 'Mastercard', amex: 'American Express', diners: 'Diners',
        paypal: 'PayPal', pagoefectivo: 'PagoEfectivo'};
    const filtros = window.Ui.barraFiltros($('barra-filtros'), [
        {id: 'texto', tipo: 'busqueda', etiqueta: 'Buscar', marcador: 'N.º, pedido, comprador o correo…'},
        {id: 'estado', tipo: 'select', etiqueta: 'Estado',
            opciones: Object.entries(ESTADOS_PAGO).map(([valor, [texto]]) => [valor, texto])},
        {id: 'medio', tipo: 'select', etiqueta: 'Medio de pago'},
        {id: 'comprador', tipo: 'select', etiqueta: 'Comprador'},
        {id: 'desde', tipo: 'fecha', etiqueta: 'Desde'},
        {id: 'hasta', tipo: 'fecha', etiqueta: 'Hasta'}
    ], pintarPagos);

    const medioClave = (p) => (p.marcaTarjeta || '').toLowerCase();
    const idCompradorDe = (p) => (pedidoDe(p.idPedido) || {}).idComprador;

    function pintarPagos() {
        const f = filtros.valores();
        const visibles = pagos.filter((p) => window.Ui.coincide(f.texto, p.idPago, p.idPedido,
            compradorDe(p.idPedido), p.emailPagador)
            && (!f.estado || p.estadoPago === f.estado)
            && (!f.medio || medioClave(p) === f.medio)
            && (!f.comprador || String(idCompradorDe(p)) === f.comprador)
            && window.Ui.entreFechas(p.fechaActualizacion, f.desde, f.hasta));
        filtros.contar(visibles.length, pagos.length);
        const filtrando = Object.values(f).some(Boolean);
        window.Ui.tabla($('lista'), [
            {titulo: 'N.º', valor: (p) => p.idPago},
            {titulo: 'Pedido', valor: (p) => p.idPedido},
            {titulo: 'Comprador', clase: 'largo', valor: (p) => compradorDe(p.idPedido)},
            {titulo: 'Monto', clase: 'numero', valor: (p) => window.Ui.dinero(p.monto)},
            {titulo: 'Estado', valor: (p) => window.Ui.etiqueta((ESTADOS_PAGO[p.estadoPago] || [p.estadoPago])[0],
                (ESTADOS_PAGO[p.estadoPago] || [])[1])},
            {titulo: 'Medio de pago', valor: medioDePago},
            {titulo: 'Correo', clase: 'largo', valor: (p) => p.emailPagador},
            {titulo: 'Fecha', valor: (p) => window.Ui.fecha(p.fechaActualizacion)}
        ], visibles, {
            vacio: filtrando ? {titulo: 'Ningún pago coincide', texto: 'Cambia la búsqueda o pulsa «Limpiar».'}
                : {titulo: 'Aún no hay pagos', texto: 'Cuando un comprador pague con su enlace, aparecerá aquí.'},
            acciones: (p) => p.estadoPago === 'APROBADO'
                ? [window.Ui.boton('Ver comprobante', 'boton-secundario', () => verComprobante(p))] : []
        });
    }

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            let liquidaciones;
            let todosLosPagos;
            [pedidos, compradores, liquidaciones, todosLosPagos] = await Promise.all([
                window.Ui.cargarTodo('/api/pedidos'), window.Api.pedir('/api/compradores'),
                window.Ui.cargarTodo('/api/liquidaciones'), window.Ui.cargarTodo('/api/pagos')]);
            pintarPorCobrar(liquidaciones, todosLosPagos);
            pagos = todosLosPagos;
            filtros.opciones('medio', window.Ui.opcionesUnicas(pagos, medioClave,
                (p) => MEDIOS[medioClave(p)] || p.marcaTarjeta));
            filtros.opciones('comprador', window.Ui.opcionesUnicas(compradores, (c) => c.idComprador,
                (c) => c.nombreComprador));
            pintarPagos();
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    window.Ui.alReintentar(cargar);
    cargar();
})();
