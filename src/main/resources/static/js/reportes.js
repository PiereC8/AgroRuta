'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'reportes', titulo: () => 'Reportes'})) {
        return;
    }
    if (!window.Ui.puedeEscribir()) {
        window.location.replace('/inicio.html');
        return;
    }

    const $ = (id) => document.getElementById(id);
    const el = window.Ui.el;
    const NS = 'http://www.w3.org/2000/svg';
    const numero = new Intl.NumberFormat('es-PE', {maximumFractionDigits: 1});
    const solesRedondos = new Intl.NumberFormat('es-PE', {style: 'currency', currency: 'PEN', maximumFractionDigits: 0});

    const PERIODOS = [
        {id: 'hoy', texto: 'Hoy'},
        {id: 'dia', texto: 'Un día'},
        {id: '7', texto: 'Últimos 7 días'},
        {id: '30', texto: 'Últimos 30 días'},
        {id: '90', texto: 'Últimos 90 días'},
        {id: 'mes', texto: 'Este mes'},
        {id: 'mes-pasado', texto: 'Mes pasado'},
        {id: 'anio', texto: 'Este año'},
        {id: 'rango', texto: 'Rango propio'}
    ];
    const VEREDICTOS = {
        GANANDO: ['Estás ganando', 'ganando'],
        GANANDO_MENOS: ['Estás ganando, pero menos que antes', 'menos'],
        PERDIENDO: ['Estás perdiendo dinero', 'perdiendo'],
        SIN_DATOS: ['Sin entregas en este período', 'sin-datos']
    };
    const CAUSAS = {VENTAS: 'Ventas', MERMA: 'Merma y rechazo', FLETE: 'Flete'};

    let periodo = '30';
    let cargando = 0;

    /** Hoy en Lima ("2026-11-07"), sin depender de la zona horaria de la computadora. */
    function hoy() {
        return new Intl.DateTimeFormat('en-CA', {timeZone: 'America/Lima'}).format(new Date());
    }

    function aTexto(fecha) {
        return fecha.toISOString().slice(0, 10);
    }

    function aFecha(texto) {
        return new Date(texto + 'T00:00:00Z');
    }

    function sumarDias(texto, dias) {
        const f = aFecha(texto);
        f.setUTCDate(f.getUTCDate() + dias);
        return aTexto(f);
    }

    function rangoDe(id) {
        const fin = hoy();
        const f = aFecha(fin);
        const anio = f.getUTCFullYear();
        const mes = f.getUTCMonth();
        switch (id) {
            case 'hoy': return {desde: fin, hasta: fin};
            case 'dia': return {desde: $('dia').value, hasta: $('dia').value};
            case '7': return {desde: sumarDias(fin, -6), hasta: fin};
            case '90': return {desde: sumarDias(fin, -89), hasta: fin};
            case 'mes': return {desde: aTexto(new Date(Date.UTC(anio, mes, 1))), hasta: fin};
            case 'mes-pasado': return {
                desde: aTexto(new Date(Date.UTC(anio, mes - 1, 1))),
                hasta: aTexto(new Date(Date.UTC(anio, mes, 0)))
            };
            case 'anio': return {desde: aTexto(new Date(Date.UTC(anio, 0, 1))), hasta: fin};
            case 'rango': return {desde: $('desde').value, hasta: $('hasta').value};
            default: return {desde: sumarDias(fin, -29), hasta: fin};
        }
    }

    function consulta() {
        const {desde, hasta} = rangoDe(periodo);
        const p = new URLSearchParams({desde, hasta});
        if ($('producto').value) {
            p.set('producto', $('producto').value);
        }
        if ($('destino').value) {
            p.set('destino', $('destino').value);
        }
        return p.toString();
    }

    function rangoValido() {
        const {desde, hasta} = rangoDe(periodo);
        if (!desde || !hasta) {
            window.Ui.errorDePagina('Elige desde qué día y hasta qué día quieres el reporte.');
            return false;
        }
        if (desde > hasta) {
            window.Ui.errorDePagina('La fecha de inicio debe ir antes de la fecha final.');
            return false;
        }
        return true;
    }

    function soles(n) {
        return window.Ui.dinero(n);
    }

    function svg(nombre, atributos, texto) {
        const e = document.createElementNS(NS, nombre);
        for (const [k, v] of Object.entries(atributos || {})) {
            e.setAttribute(k, v);
        }
        if (texto !== undefined) {
            e.textContent = texto;
        }
        return e;
    }

    function pintarPeriodos() {
        const caja = $('periodos');
        caja.replaceChildren();
        for (const p of PERIODOS) {
            const b = el('button', 'filtro', p.texto);
            b.type = 'button';
            b.setAttribute('aria-pressed', String(p.id === periodo));
            b.addEventListener('click', () => elegir(p.id));
            caja.appendChild(b);
        }
        $('rango').classList.toggle('oculto', periodo !== 'rango');
        $('dia-elegido').classList.toggle('oculto', periodo !== 'dia');
    }

    function elegir(id) {
        periodo = id;
        if (id === 'dia' && !$('dia').value) {
            $('dia').value = hoy();
        }
        pintarPeriodos();
        if (id === 'rango') {
            if (!$('desde').value) {
                $('hasta').value = hoy();
                $('desde').value = sumarDias(hoy(), -29);
            }
            return;
        }
        cargar();
    }

    function pintarVeredicto(r) {
        const [titulo, clase] = VEREDICTOS[r.veredicto] || [r.veredicto, 'sin-datos'];
        const caja = $('veredicto');
        caja.className = 'veredicto veredicto--' + clase + ' bloque';
        caja.replaceChildren(el('strong', 'veredicto__titulo', titulo), el('p', 'veredicto__texto', r.explicacion));
    }

    function tarjeta(titulo, valor, nota, tono) {
        const t = el('div', 'tarjeta');
        t.append(el('div', 'tarjeta__titulo', titulo), el('div', 'cifra cifra--mediana' + (tono ? ' ' + tono : ''), valor));
        if (nota) {
            t.appendChild(el('div', 'suave', nota));
        }
        return t;
    }

    function pintarCifras(r) {
        const a = r.actual;
        const caja = $('cifras');
        caja.replaceChildren(
            tarjeta('Ganancia real', soles(a.gananciaReal), null, a.gananciaReal < 0 ? 'dato-perdida' : ''),
            tarjeta('Ingreso real', soles(a.ingresoReal), a.entregas + ' entregas'),
            tarjeta('Flete', soles(a.fleteTotal)),
            tarjeta('Merma y rechazo', soles(a.perdidaMermaRechazo), numero.format(Number(a.mermaKg) + Number(a.rechazoKg)) + ' kg perdidos'),
            tarjeta('Margen', a.margenPorcentaje === null ? '—' : numero.format(a.margenPorcentaje) + ' %'),
            tarjeta('Por cobrar', soles(a.porCobrar), 'Cobrado: ' + soles(a.cobrado)));
    }

    function pintarCausas(r) {
        const destino = $('causas');
        if (r.veredicto === 'SIN_DATOS' || r.anterior.entregas === 0) {
            destino.replaceChildren(el('p', 'suave', 'No hay un período anterior con entregas para explicar el cambio.'));
            return;
        }
        const lista = el('ul', 'causas');
        for (const c of r.causas) {
            const suma = Number(c.efecto) >= 0;
            const fila = el('li', 'causas__fila');
            fila.append(el('span', null, CAUSAS[c.concepto] || c.concepto),
                el('strong', suma ? 'tendencia--sube' : 'tendencia--baja',
                    (suma ? '+' : '−') + soles(Math.abs(Number(c.efecto))).replace('-', '')),
                el('span', 'suave', suma ? 'sumó a tu ganancia' : 'restó a tu ganancia'));
            lista.appendChild(fila);
        }
        destino.replaceChildren(lista);
    }

    function valorDe(c, n) {
        if (n === null || n === undefined) {
            return '—';
        }
        return c.unidad === 'SOLES' ? soles(n) : c.unidad === 'KILOS' ? window.Ui.kilos(n) : String(n);
    }

    function pintarComparacion(r) {
        window.Ui.tabla($('comparacion'), [
            {titulo: 'Concepto', valor: (c) => c.concepto},
            {titulo: 'Este período', clase: 'numero', valor: (c) => valorDe(c, c.actual)},
            {titulo: 'Anterior', clase: 'numero', valor: (c) => valorDe(c, c.anterior)},
            {titulo: 'Diferencia', clase: 'numero', valor: (c) => valorDe(c, c.diferencia)},
            {titulo: 'Cambio', clase: 'numero', valor: (c) => {
                if (c.porcentaje === null) {
                    return '—';
                }
                const marca = el('span', c.tendencia === 'MEJORA' ? 'tendencia--sube'
                    : c.tendencia === 'EMPEORA' ? 'tendencia--baja' : '', (c.porcentaje > 0 ? '+' : '') + numero.format(c.porcentaje) + ' %');
                return marca;
            }}
        ], r.comparaciones, {vacio: {titulo: 'Sin datos para comparar'}});
    }

    function pintarGrafico(serie) {
        const destino = $('grafico');
        if (!serie.some((p) => Number(p.ganancia) !== 0 || Number(p.ingreso) !== 0)) {
            destino.replaceChildren(el('p', 'suave', 'No hay entregas en este período.'));
            return;
        }
        const ancho = 900;
        const alto = 260;
        const margenSup = 24;
        const margenInf = 34;
        const valores = serie.map((p) => Number(p.ganancia));
        const maximo = Math.max(0, ...valores);
        const minimo = Math.min(0, ...valores);
        const rango = (maximo - minimo) || 1;
        const util = alto - margenSup - margenInf;
        const cero = margenSup + (maximo / rango) * util;
        const paso = ancho / serie.length;
        const barra = Math.max(3, Math.min(paso * 0.7, 48));
        const cada = Math.ceil(serie.length / 14);
        const lienzo = svg('svg', {viewBox: '0 0 ' + ancho + ' ' + alto, role: 'img',
            'aria-label': 'Ganancia por tramo del período'});
        serie.forEach((p, i) => {
            const v = Number(p.ganancia);
            const altura = Math.abs(v) / rango * util;
            const x = i * paso + (paso - barra) / 2;
            const rect = svg('rect', {x, y: v < 0 ? cero : cero - altura, width: barra, height: Math.max(altura, 1),
                rx: 4, 'class': 'barra' + (v < 0 ? ' barra--perdida' : '')});
            rect.appendChild(svg('title', {}, p.etiqueta + ': ' + soles(v)));
            lienzo.appendChild(rect);
            if (i % cada === 0) {
                lienzo.appendChild(svg('text', {x: x + barra / 2, y: alto - 10, 'text-anchor': 'middle'}, p.etiqueta));
            }
            if (serie.length <= 14 && v !== 0) {
                lienzo.appendChild(svg('text', {x: x + barra / 2, y: v < 0 ? cero + altura + 14 : cero - altura - 6,
                    'text-anchor': 'middle', 'class': 'grafico__valor'}, solesRedondos.format(v)));
            }
        });
        lienzo.appendChild(svg('line', {x1: 0, x2: ancho, y1: cero, y2: cero, 'class': 'grafico__eje'}));
        destino.replaceChildren(lienzo);
    }

    function pintarRanking(id, filas, titulo) {
        window.Ui.tabla($(id), [
            {titulo, valor: (f) => f.nombre},
            {titulo: 'Entregas', clase: 'numero', valor: (f) => f.entregas},
            {titulo: 'Ganancia', clase: 'numero', valor: (f) => el('span', Number(f.ganancia) < 0 ? 'dato-perdida' : '', soles(f.ganancia))},
            {titulo: 'Margen', clase: 'numero', valor: (f) => f.margenPorcentaje === null ? '—' : numero.format(f.margenPorcentaje) + ' %'},
            {titulo: 'Merma', clase: 'numero', valor: (f) => window.Ui.kilos(f.mermaKg)}
        ], filas, {vacio: {titulo: 'Sin entregas'}});
    }

    async function cargar() {
        if (!rangoValido()) {
            return;
        }
        const turno = ++cargando;
        window.Ui.errorDePagina('');
        const q = consulta();
        try {
            const [resumen, serie, destinos, productos, compradores] = await Promise.all([
                window.Api.pedir('/api/reportes/resumen?' + q),
                window.Api.pedir('/api/reportes/serie?' + q),
                window.Api.pedir('/api/reportes/ranking?dimension=destino&' + q),
                window.Api.pedir('/api/reportes/ranking?dimension=producto&' + q),
                window.Api.pedir('/api/reportes/ranking?dimension=comprador&' + q)]);
            if (turno !== cargando) {
                return;
            }
            const unDia = resumen.desde === resumen.hasta;
            $('periodo-texto').textContent = (unDia
                ? 'El ' + window.Ui.dia(resumen.desde) + ', comparado con el ' + window.Ui.dia(resumen.anteriorDesde) + '.'
                : 'Del ' + window.Ui.dia(resumen.desde) + ' al ' + window.Ui.dia(resumen.hasta) + ', comparado con el '
                    + window.Ui.dia(resumen.anteriorDesde) + ' al ' + window.Ui.dia(resumen.anteriorHasta) + '.')
                + ' El PDF y el CSV se descargan con este mismo período y filtros.';
            pintarVeredicto(resumen);
            pintarCifras(resumen);
            pintarCausas(resumen);
            pintarComparacion(resumen);
            pintarGrafico(serie);
            pintarRanking('rank-destino', destinos, 'Destino');
            pintarRanking('rank-producto', productos, 'Producto');
            pintarRanking('rank-comprador', compradores, 'Comprador');
        } catch (e) {
            if (turno === cargando) {
                window.Ui.errorDePagina(e.message);
            }
        }
    }

    async function cargarOpciones() {
        try {
            const opciones = await window.Api.pedir('/api/reportes/opciones');
            for (const p of opciones.productos) {
                const o = el('option', null, p);
                o.value = p;
                $('producto').appendChild(o);
            }
            for (const d of opciones.destinos) {
                const o = el('option', null, d.nombre);
                o.value = d.idDestinoVenta;
                $('destino').appendChild(o);
            }
        } catch (e) {
            window.Ui.errorDePagina(e.message);
        }
    }

    async function descargar(formato) {
        if (!rangoValido()) {
            return;
        }
        const boton = $('exportar-' + formato);
        boton.disabled = true;
        try {
            const token = sessionStorage.getItem('agroruta.token');
            const respuesta = await fetch('/api/reportes/exportar?formato=' + formato + '&' + consulta(),
                {headers: {'Authorization': 'Bearer ' + token}, cache: 'no-store'});
            if (!respuesta.ok) {
                const datos = await respuesta.json().catch(() => null);
                throw new Error((datos && datos.error) || 'No se pudo generar el archivo. Inténtalo de nuevo.');
            }
            const contenido = await respuesta.blob();
            const nombre = /filename="?([^";]+)"?/.exec(respuesta.headers.get('Content-Disposition') || '');
            const enlace = document.createElement('a');
            const url = URL.createObjectURL(contenido);
            enlace.href = url;
            enlace.download = nombre ? nombre[1] : 'reporte-agroruta.' + formato;
            document.body.appendChild(enlace);
            enlace.click();
            enlace.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (e) {
            window.Ui.errorDePagina(e.message);
        } finally {
            boton.disabled = false;
        }
    }

    $('producto').addEventListener('change', cargar);
    $('destino').addEventListener('change', cargar);
    $('aplicar-rango').addEventListener('click', cargar);
    $('dia').addEventListener('change', () => { if ($('dia').value) { cargar(); } });
    $('exportar-csv').addEventListener('click', () => descargar('csv'));
    $('exportar-pdf').addEventListener('click', () => descargar('pdf'));
    window.Ui.alReintentar(cargar);

    pintarPeriodos();
    cargarOpciones();
    cargar();
})();
