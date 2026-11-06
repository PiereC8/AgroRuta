'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'inicio'})) {
        return;
    }

    const $ = (id) => document.getElementById(id);
    const NS = 'http://www.w3.org/2000/svg';
    const soles = new Intl.NumberFormat('es-PE', {style: 'currency', currency: 'PEN'});
    const solesRedondos = new Intl.NumberFormat('es-PE', {style: 'currency', currency: 'PEN', maximumFractionDigits: 0});
    const numero = new Intl.NumberFormat('es-PE', {maximumFractionDigits: 1});
    const ESTADOS = {PROGRAMADO: 'Programado', EN_RUTA: 'En ruta'};

    function nombreDelMes(texto, formato) {
        const [anio, mes] = texto.split('-').map(Number);
        return new Intl.DateTimeFormat('es-PE', {month: formato, timeZone: 'UTC'})
            .format(new Date(Date.UTC(anio, mes - 1, 1))).toLowerCase();
    }

    function kilos(valor) {
        return numero.format(valor) + ' kg';
    }

    function poner(id, texto) {
        const elemento = $(id);
        elemento.replaceChildren();
        elemento.textContent = texto;
    }

    function celda(texto, clase) {
        const td = document.createElement('td');
        td.textContent = texto;
        if (clase) {
            td.className = clase;
        }
        return td;
    }

    // ---------- Indicadores ----------

    function pintarGanancia(datos) {
        const mes = nombreDelMes(datos.mes, 'long');
        poner('titulo-ganancia', 'Ganancia de ' + mes);
        poner('ganancia-mes', soles.format(datos.gananciaMes));
        const detalle = $('ganancia-detalle');
        detalle.replaceChildren();
        if (datos.variacionPorcentual !== undefined) {
            const sube = datos.variacionPorcentual >= 0;
            const marca = document.createElement('span');
            marca.className = sube ? 'tendencia--sube' : 'tendencia--baja';
            marca.textContent = (sube ? '▲ ' : '▼ ') + numero.format(Math.abs(datos.variacionPorcentual)) + ' %';
            detalle.append(marca, document.createTextNode((sube ? ' más' : ' menos') + ' que el mes pasado'));
        } else if (Number(datos.gananciaMes) === 0) {
            detalle.textContent = 'Aún no hay entregas este mes.';
        } else {
            detalle.textContent = 'Sin dato del mes anterior para comparar.';
        }
    }

    function pintarEnvios(datos) {
        const lista = datos.enviosEnCurso;
        poner('envios-en-curso', String(lista.length));
        const enRuta = lista.filter((e) => e.estadoEnvio === 'EN_RUTA').length;
        poner('envios-detalle', lista.length === 0
            ? 'No hay envíos en camino.'
            : enRuta + ' en ruta · ' + (lista.length - enRuta) + ' programados');
    }

    function pintarMerma(datos) {
        poner('titulo-merma', 'Merma de ' + nombreDelMes(datos.mes, 'long'));
        poner('merma', kilos(datos.mermaMesKg));
        const detalle = $('merma-detalle');
        detalle.replaceChildren();
        const perdida = document.createElement('span');
        perdida.className = 'dato-perdida';
        perdida.textContent = soles.format(datos.perdidaMermaMes);
        const lineaPerdida = document.createElement('div');
        lineaPerdida.append(document.createTextNode('Pérdida con rechazo: '), perdida);
        const lineaTotal = document.createElement('div');
        lineaTotal.textContent = 'Acumulada: ' + kilos(datos.mermaTotalKg);
        detalle.append(lineaPerdida, lineaTotal);
    }

    function pintarCobros(datos) {
        const cobros = datos.cobrosPendientes;
        poner('cobros-pendientes', soles.format(cobros.monto));
        poner('cobros-detalle', cobros.cantidad === 0
            ? 'Todo lo entregado está pagado.'
            : cobros.cantidad + (cobros.cantidad === 1 ? ' entrega sin pagar' : ' entregas sin pagar'));
        $('tarjeta-cobros').classList.toggle('tarjeta--atencion', cobros.cantidad > 0);
    }

    // ---------- Envíos en camino ----------

    function pintarTablaEnvios(datos) {
        const destino = $('tabla-envios');
        destino.replaceChildren();
        if (datos.enviosEnCurso.length === 0) {
            const vacio = document.createElement('div');
            vacio.className = 'vacio';
            const titulo = document.createElement('strong');
            titulo.textContent = 'No hay envíos en camino';
            vacio.append(titulo, document.createTextNode('Cuando despaches un pedido aparecerá aquí.'));
            destino.appendChild(vacio);
            return;
        }
        const tabla = document.createElement('table');
        tabla.className = 'tabla';
        const cabecera = document.createElement('tr');
        for (const texto of ['Destino', 'Comprador', 'Estado', 'Kilos']) {
            const th = document.createElement('th');
            th.scope = 'col';
            th.textContent = texto;
            cabecera.appendChild(th);
        }
        const encabezado = document.createElement('thead');
        encabezado.appendChild(cabecera);
        const cuerpo = document.createElement('tbody');
        for (const envio of datos.enviosEnCurso) {
            const fila = document.createElement('tr');
            fila.append(celda(envio.destino), celda(envio.comprador));
            const estado = document.createElement('td');
            const etiqueta = document.createElement('span');
            etiqueta.className = 'etiqueta-estado' + (envio.estadoEnvio === 'PROGRAMADO' ? ' etiqueta-estado--espera' : '');
            etiqueta.textContent = ESTADOS[envio.estadoEnvio] || envio.estadoEnvio;
            estado.appendChild(etiqueta);
            fila.append(estado, celda(kilos(envio.cantidadKg)));
            cuerpo.appendChild(fila);
        }
        tabla.append(encabezado, cuerpo);
        destino.appendChild(tabla);
    }

    // ---------- Gráfico de barras (SVG escrito a mano) ----------

    function svg(nombre, atributos, texto) {
        const nodo = document.createElementNS(NS, nombre);
        for (const [clave, valor] of Object.entries(atributos || {})) {
            nodo.setAttribute(clave, String(valor));
        }
        if (texto !== undefined) {
            nodo.textContent = texto;
        }
        return nodo;
    }

    function pintarGrafico(datos) {
        const destino = $('grafico-ganancia');
        destino.replaceChildren();
        const serie = datos.gananciaUltimosMeses;
        const maximo = Math.max(...serie.map((m) => Number(m.ganancia)));
        if (maximo <= 0) {
            const vacio = document.createElement('div');
            vacio.className = 'vacio';
            const titulo = document.createElement('strong');
            titulo.textContent = 'Aún no hay ganancia registrada';
            vacio.append(titulo, document.createTextNode('Aparecerá cuando registres la primera entrega.'));
            destino.appendChild(vacio);
            return;
        }
        const ancho = 560;
        const alto = 260;
        const base = alto - 36;
        const arriba = 34;
        const lienzo = svg('svg', {viewBox: '0 0 ' + ancho + ' ' + alto, role: 'img',
            'aria-label': 'Ganancia de los últimos 6 meses'});
        const hueco = ancho / serie.length;
        const anchoBarra = Math.min(56, hueco - 18);
        serie.forEach((punto, i) => {
            const valor = Number(punto.ganancia);
            const altoBarra = Math.max(valor > 0 ? 4 : 0, (valor / maximo) * (base - arriba));
            const x = i * hueco + (hueco - anchoBarra) / 2;
            const actual = i === serie.length - 1;
            const barra = svg('rect', {x, y: base - altoBarra, width: anchoBarra, height: altoBarra, rx: 8,
                'class': 'barra' + (actual ? ' barra--actual' : '')});
            barra.appendChild(svg('title', {}, nombreDelMes(punto.mes, 'long') + ': ' + soles.format(valor)));
            lienzo.appendChild(barra);
            if (valor > 0) {
                lienzo.appendChild(svg('text', {x: x + anchoBarra / 2, y: base - altoBarra - 8,
                    'text-anchor': 'middle', 'class': actual ? 'grafico__valor' : ''}, solesRedondos.format(valor)));
            }
            lienzo.appendChild(svg('text', {x: x + anchoBarra / 2, y: base + 22, 'text-anchor': 'middle'},
                nombreDelMes(punto.mes, 'short').replace('.', '')));
        });
        lienzo.appendChild(svg('line', {x1: 0, x2: ancho, y1: base, y2: base, 'class': 'grafico__eje'}));
        destino.appendChild(lienzo);
    }

    // ---------- Carga ----------

    function pintar(datos) {
        const dueno = datos.gananciaMes !== undefined;
        $('tarjeta-ganancia').classList.toggle('oculto', !dueno);
        $('tarjeta-grafico').classList.toggle('oculto', !dueno);
        document.querySelector('.rejilla-cifras').classList.toggle('rejilla-cifras--tres', !dueno);
        $('rejilla-principal').classList.toggle('rejilla-principal--una', !dueno);
        if (dueno) {
            pintarGanancia(datos);
            pintarGrafico(datos);
        }
        pintarEnvios(datos);
        pintarMerma(datos);
        pintarCobros(datos);
        pintarTablaEnvios(datos);
    }

    async function cargar() {
        $('error').classList.add('oculto');
        try {
            pintar(await window.Api.pedir('/api/panel/resumen'));
        } catch (error) {
            poner('error-texto', error.message);
            $('error').classList.remove('oculto');
        }
    }

    $('reintentar').addEventListener('click', cargar);
    cargar();
})();
