'use strict';

window.Ui = (function () {
    const formatoSoles = new Intl.NumberFormat('es-PE', {style: 'currency', currency: 'PEN'});
    const formatoNumero = new Intl.NumberFormat('es-PE', {maximumFractionDigits: 3});
    const formatoFecha = new Intl.DateTimeFormat('es-PE', {dateStyle: 'medium', timeStyle: 'short'});
    const formatoDia = new Intl.DateTimeFormat('es-PE', {dateStyle: 'medium', timeZone: 'UTC'});
    const TAMANO_PAGINA = 50;

    function el(etiqueta, clase, texto) {
        const e = document.createElement(etiqueta);
        if (clase) {
            e.className = clase;
        }
        if (texto !== undefined && texto !== null) {
            e.textContent = texto;
        }
        return e;
    }

    function dinero(n) {
        return n === null || n === undefined ? '—' : formatoSoles.format(n);
    }

    function kilos(n) {
        return n === null || n === undefined ? '—' : formatoNumero.format(n) + ' kg';
    }

    function fecha(instante) {
        return instante ? formatoFecha.format(new Date(instante)) : '—';
    }

    /** Fecha sin hora ("2026-11-06"), sin que la zona horaria la mueva de día. */
    function dia(texto) {
        return texto ? formatoDia.format(new Date(texto + 'T00:00:00Z')) : '—';
    }

    function puedeEscribir() {
        const usuario = window.Api.usuario();
        return Boolean(usuario) && usuario.rolUsuario === 'DUENO';
    }

    function cargarPagina(ruta, opciones) {
        const pagina = (opciones && opciones.pagina) || 0;
        const tamano = (opciones && opciones.tamano) || TAMANO_PAGINA;
        return window.Api.pedir(ruta + (ruta.includes('?') ? '&' : '?') + 'pagina=' + pagina + '&tamano=' + tamano);
    }

    function etiqueta(texto, tono) {
        return el('span', 'etiqueta-estado' + (tono ? ' etiqueta-estado--' + tono : ''), texto);
    }

    function boton(texto, clase, alPulsar) {
        const b = el('button', 'boton ' + (clase || ''), texto);
        b.type = 'button';
        if (alPulsar) {
            b.addEventListener('click', alPulsar);
        }
        return b;
    }

    function tabla(destino, columnas, filas, opciones) {
        const o = opciones || {};
        destino.replaceChildren();
        if (!filas || filas.length === 0) {
            const vacio = el('div', 'vacio');
            vacio.append(el('strong', null, (o.vacio && o.vacio.titulo) || 'No hay nada para mostrar'));
            if (o.vacio && o.vacio.texto) {
                vacio.append(document.createTextNode(o.vacio.texto));
            }
            if (o.vacio && o.vacio.accion) {
                vacio.append(document.createElement('br'), o.vacio.accion);
            }
            destino.appendChild(vacio);
            return;
        }
        const envoltorio = el('div', 'tabla-envoltorio');
        const t = el('table', 'tabla');
        const cabecera = el('tr');
        for (const columna of columnas) {
            const th = el('th', columna.clase, columna.titulo);
            th.scope = 'col';
            cabecera.appendChild(th);
        }
        if (o.acciones) {
            const th = el('th', null, 'Acciones');
            th.scope = 'col';
            cabecera.appendChild(th);
        }
        const encabezado = el('thead');
        encabezado.appendChild(cabecera);
        const cuerpo = el('tbody');
        for (const fila of filas) {
            const tr = el('tr', o.filaDestacada && o.filaDestacada(fila) ? 'fila--destacada' : '');
            for (const columna of columnas) {
                const td = el('td', columna.clase);
                const valor = columna.valor(fila);
                if (valor instanceof Node) {
                    td.appendChild(valor);
                } else {
                    td.textContent = valor === null || valor === undefined || valor === '' ? '—' : String(valor);
                }
                tr.appendChild(td);
            }
            if (o.acciones) {
                const td = el('td');
                const caja = el('div', 'fila-acciones');
                caja.append(...o.acciones(fila));
                td.appendChild(caja);
                tr.appendChild(td);
            }
            cuerpo.appendChild(tr);
        }
        t.append(encabezado, cuerpo);
        envoltorio.appendChild(t);
        destino.appendChild(envoltorio);
    }

    function aviso(texto, tipo) {
        let caja = document.getElementById('avisos');
        if (!caja) {
            caja = el('div', 'avisos');
            caja.id = 'avisos';
            caja.setAttribute('role', 'status');
            caja.setAttribute('aria-live', 'polite');
            document.body.appendChild(caja);
        }
        const nuevo = el('div', 'toast' + (tipo === 'error' ? ' toast--error' : ''), texto);
        caja.appendChild(nuevo);
        setTimeout(() => nuevo.remove(), 5000);
    }

    function pestanas(destino, items) {
        destino.replaceChildren();
        const lista = el('div', 'pestanas');
        lista.setAttribute('role', 'tablist');
        const botones = [];
        function activar(indice, enfocar) {
            items.forEach((item, i) => {
                const activa = i === indice;
                botones[i].setAttribute('aria-selected', activa ? 'true' : 'false');
                botones[i].tabIndex = activa ? 0 : -1;
                item.vista.classList.toggle('oculto', !activa);
            });
            if (enfocar) {
                botones[indice].focus();
            }
            if (items[indice].alActivar) {
                items[indice].alActivar();
            }
        }
        items.forEach((item, i) => {
            const b = el('button', 'pestana', item.texto);
            b.type = 'button';
            b.setAttribute('role', 'tab');
            b.addEventListener('click', () => activar(i, false));
            b.addEventListener('keydown', (e) => {
                if (e.key === 'ArrowRight' || e.key === 'ArrowLeft') {
                    e.preventDefault();
                    activar((i + (e.key === 'ArrowRight' ? 1 : items.length - 1)) % items.length, true);
                }
            });
            botones.push(b);
            lista.appendChild(b);
        });
        destino.appendChild(lista);
        for (const item of items) {
            destino.appendChild(item.vista);
        }
        activar(0, false);
        return {activar};
    }

    function mensajeObligatorio(campo) {
        return 'Completa ' + campo.etiqueta.toLowerCase() + '.';
    }

    function revisarCampo(campo, entrada, todos) {
        const texto = entrada.value.trim();
        if (campo.requerido && texto === '') {
            return campo.mensajeObligatorio || mensajeObligatorio(campo);
        }
        if (texto !== '' && campo.tipo === 'numero') {
            const n = Number(texto.replace(',', '.'));
            if (!Number.isFinite(n)) {
                return 'Escribe solo números, por ejemplo 12.5.';
            }
            if (!campo.permiteCero && n <= 0) {
                return 'Debe ser mayor que cero.';
            }
            if (campo.permiteCero && n < 0) {
                return 'No puede ser negativo.';
            }
            if (campo.maximo !== undefined && n > campo.maximo) {
                return 'No puede ser mayor que ' + campo.maximo + '.';
            }
        }
        if (campo.validar) {
            return campo.validar(texto, todos) || null;
        }
        return null;
    }

    function valorDe(campo, entrada) {
        const texto = entrada.value.trim();
        if (campo.tipo === 'numero') {
            return texto === '' ? null : Number(texto.replace(',', '.'));
        }
        return texto;
    }

    function panel(config) {
        const quienAbrio = document.activeElement;
        return new Promise((resolver) => {
            const fondo = el('div', 'fondo-panel');
            const caja = el('aside', 'panel' + (config.ancho ? ' panel--ancho' : ''));
            caja.setAttribute('role', 'dialog');
            caja.setAttribute('aria-modal', 'true');
            const idTitulo = 'panel-titulo-' + Date.now();
            caja.setAttribute('aria-labelledby', idTitulo);

            const cabecera = el('div', 'panel__cabecera');
            const titulo = el('h2', null, config.titulo);
            titulo.id = idTitulo;
            // Una sola forma visible de salir: los paneles de solo lectura tienen "Cerrar" arriba y no tienen pie;
            // los de formulario tienen "Cancelar" abajo y nada arriba (Escape también cierra a ambos).
            const soloLectura = Boolean(config.soloLectura);
            const cerrar = boton('Cerrar', 'boton-secundario');
            cerrar.setAttribute('aria-label', 'Cerrar ' + config.titulo);
            cabecera.appendChild(titulo);
            if (soloLectura) {
                cabecera.appendChild(cerrar);
            }

            const formulario = el('form', 'panel__cuerpo');
            formulario.noValidate = true;
            const errorGeneral = el('div', 'aviso aviso--error oculto');
            errorGeneral.setAttribute('role', 'alert');
            errorGeneral.tabIndex = -1;
            formulario.appendChild(errorGeneral);
            if (config.texto) {
                formulario.appendChild(el('p', 'suave panel__texto', config.texto));
            }
            if (config.contenido) {
                formulario.appendChild(config.contenido);
            }

            const entradas = new Map();
            for (const campo of config.campos || []) {
                const caja2 = el('div', 'campo');
                const idEntrada = 'campo-' + campo.id;
                const etiquetaCampo = el('label', 'campo__etiqueta', campo.etiqueta + (campo.requerido ? '' : ' (opcional)'));
                etiquetaCampo.htmlFor = idEntrada;
                let entrada;
                if (campo.tipo === 'select') {
                    entrada = el('select', 'campo__entrada');
                    const primera = el('option', null, campo.marcador || 'Elige una opción');
                    primera.value = '';
                    entrada.appendChild(primera);
                    for (const opcion of campo.opciones || []) {
                        const o = el('option', null, opcion.texto);
                        o.value = String(opcion.valor);
                        entrada.appendChild(o);
                    }
                } else {
                    entrada = el('input', 'campo__entrada');
                    entrada.type = campo.tipo === 'fecha' ? 'date' : campo.tipo === 'telefono' ? 'tel' : 'text';
                    if (campo.tipo === 'numero') {
                        entrada.inputMode = 'decimal';
                    }
                    entrada.autocomplete = 'off';
                    if (campo.marcador) {
                        entrada.placeholder = campo.marcador;
                    }
                    if (campo.maximoFecha) {
                        entrada.max = campo.maximoFecha;
                    }
                }
                entrada.id = idEntrada;
                if (campo.valor !== undefined && campo.valor !== null) {
                    entrada.value = String(campo.valor);
                }
                const describe = [];
                const error = el('span', 'campo__error oculto');
                error.id = idEntrada + '-error';
                caja2.append(etiquetaCampo, entrada);
                if (campo.ayuda) {
                    const ayuda = el('span', 'campo__ayuda', campo.ayuda);
                    ayuda.id = idEntrada + '-ayuda';
                    describe.push(ayuda.id);
                    caja2.appendChild(ayuda);
                }
                describe.push(error.id);
                entrada.setAttribute('aria-describedby', describe.join(' '));
                caja2.appendChild(error);
                formulario.appendChild(caja2);
                entradas.set(campo.id, {campo, entrada, error, caja: caja2});
            }

            const nota = el('p', 'panel__nota');
            nota.setAttribute('aria-live', 'polite');
            formulario.appendChild(nota);

            const pie = el('div', 'panel__pie');
            const cancelar = boton('Cancelar', 'boton-secundario');
            const enviar = el('button', 'boton', config.textoEnviar || 'Guardar');
            enviar.type = 'submit';
            pie.append(cancelar, enviar);
            if (!soloLectura) {
                formulario.appendChild(pie);
            }
            caja.append(cabecera, formulario);
            fondo.appendChild(caja);

            function valores() {
                const resultado = {};
                for (const [id, parte] of entradas) {
                    resultado[id] = valorDe(parte.campo, parte.entrada);
                }
                return resultado;
            }

            function marcar(parte, mensaje) {
                parte.caja.classList.toggle('campo--error', Boolean(mensaje));
                parte.entrada.setAttribute('aria-invalid', mensaje ? 'true' : 'false');
                parte.error.textContent = mensaje || '';
                parte.error.classList.toggle('oculto', !mensaje);
            }

            function revisar(parte) {
                const mensaje = revisarCampo(parte.campo, parte.entrada, valores());
                marcar(parte, mensaje);
                return !mensaje;
            }

            function actualizarNota() {
                nota.textContent = config.nota ? (config.nota(valores()) || '') : '';
            }

            for (const parte of entradas.values()) {
                parte.entrada.addEventListener('blur', () => {
                    if (parte.entrada.value.trim() !== '' || parte.caja.classList.contains('campo--error')) {
                        revisar(parte);
                    }
                });
                const alCambiar = () => {
                    if (parte.caja.classList.contains('campo--error')) {
                        revisar(parte);
                    }
                    actualizarNota();
                    if (config.alCambiar) {
                        config.alCambiar(valores(), entradas);
                    }
                };
                parte.entrada.addEventListener('input', alCambiar);
                parte.entrada.addEventListener('change', alCambiar);
            }

            function terminar(resultado) {
                document.removeEventListener('keydown', alTeclado, true);
                fondo.remove();
                if (quienAbrio && quienAbrio.focus) {
                    quienAbrio.focus();
                }
                resolver(resultado);
            }

            function alTeclado(e) {
                if (e.key === 'Escape') {
                    e.preventDefault();
                    terminar(null);
                    return;
                }
                if (e.key === 'Tab') {
                    const enfocables = [...caja.querySelectorAll('button, input, select, a[href], [tabindex="0"]')]
                        .filter((x) => !x.disabled && !x.closest('.oculto'));
                    if (enfocables.length === 0) {
                        return;
                    }
                    const primero = enfocables[0];
                    const ultimo = enfocables[enfocables.length - 1];
                    if (e.shiftKey && document.activeElement === primero) {
                        e.preventDefault();
                        ultimo.focus();
                    } else if (!e.shiftKey && document.activeElement === ultimo) {
                        e.preventDefault();
                        primero.focus();
                    }
                }
            }

            let enCurso = false;
            formulario.addEventListener('submit', async (e) => {
                e.preventDefault();
                if (enCurso) {
                    return;
                }
                errorGeneral.classList.add('oculto');
                const fallos = [...entradas.values()].filter((parte) => !revisar(parte));
                if (fallos.length > 0) {
                    fallos[0].entrada.focus();
                    return;
                }
                enCurso = true;
                enviar.disabled = true;
                const textoOriginal = enviar.textContent;
                enviar.textContent = 'Guardando…';
                try {
                    const resultado = config.enviar ? await config.enviar(valores()) : true;
                    terminar(resultado === undefined ? true : resultado);
                } catch (error) {
                    errorGeneral.textContent = error.message;
                    errorGeneral.classList.remove('oculto');
                    errorGeneral.focus();
                    enviar.disabled = false;
                    enviar.textContent = textoOriginal;
                    enCurso = false;
                }
            });
            cerrar.addEventListener('click', () => terminar(null));
            cancelar.addEventListener('click', () => terminar(null));
            fondo.addEventListener('mousedown', (e) => {
                if (e.target === fondo) {
                    terminar(null);
                }
            });

            document.body.appendChild(fondo);
            document.addEventListener('keydown', alTeclado, true);
            actualizarNota();
            const primerCampo = entradas.size > 0 ? [...entradas.values()][0].entrada : (soloLectura ? cerrar : enviar);
            primerCampo.focus();
        });
    }

    function detalle(config) {
        return panel({titulo: config.titulo, texto: config.texto, contenido: config.contenido, ancho: config.ancho,
            campos: [], soloLectura: true});
    }

    async function confirmar(config) {
        const respuesta = await panel({titulo: config.titulo, texto: config.texto, campos: [],
            textoEnviar: config.textoAceptar || 'Aceptar', enviar: config.enviar || (async () => true)});
        return respuesta !== null;
    }

    function listaPaginada(config) {
        let pagina = 0;
        let todas = [];
        async function siguiente(reiniciar) {
            if (reiniciar) {
                pagina = 0;
                todas = [];
            }
            const lote = await cargarPagina(config.ruta, {pagina});
            todas = todas.concat(lote);
            pagina += 1;
            config.alCargar(todas);
            config.mas.replaceChildren();
            if (lote.length >= TAMANO_PAGINA) {
                config.mas.appendChild(boton('Ver más', 'boton-secundario', () => {
                    siguiente(false).catch((error) => errorDePagina(error.message));
                }));
            }
        }
        return {cargar: () => siguiente(true)};
    }

    function campoCopiable(titulo, texto) {
        const caja = el('div', 'bloque');
        caja.appendChild(el('h3', null, titulo));
        const fila = el('div', 'enlace-copiable');
        const entrada = el('input', 'campo__entrada');
        entrada.readOnly = true;
        entrada.value = texto;
        entrada.setAttribute('aria-label', titulo);
        const copiar = boton('Copiar', 'boton-secundario', async () => {
            try {
                await navigator.clipboard.writeText(entrada.value);
            } catch (e) {
                entrada.select();
                document.execCommand('copy');
            }
            copiar.textContent = 'Copiado';
            setTimeout(() => { copiar.textContent = 'Copiar'; }, 2000);
        });
        fila.append(entrada, copiar);
        caja.appendChild(fila);
        return caja;
    }

    /** Código QR de un texto (por ejemplo un enlace), dibujado en el navegador con la biblioteca propia. */
    function codigoQr(texto, descripcion) {
        const figura = el('figure', 'codigo-qr');
        const qr = window.qrcode(0, 'M');
        qr.addData(texto);
        qr.make();
        const imagen = el('img');
        imagen.src = qr.createDataURL(5, 2);
        imagen.alt = 'Código QR: ' + descripcion;
        imagen.width = 220;
        imagen.height = 220;
        figura.append(imagen, el('figcaption', 'suave', descripcion));
        return figura;
    }

    function errorDePagina(mensaje) {
        const caja = document.getElementById('error');
        if (!caja) {
            return;
        }
        document.getElementById('error-texto').textContent = mensaje || '';
        caja.classList.toggle('oculto', !mensaje);
    }

    function alReintentar(cargar) {
        const boton = document.getElementById('reintentar');
        if (boton) {
            boton.addEventListener('click', cargar);
        }
    }

    return {el, boton, dinero, kilos, fecha, dia, puedeEscribir, cargarPagina, etiqueta, tabla, aviso, pestanas,
        panel, detalle, confirmar, errorDePagina, alReintentar, listaPaginada, campoCopiable, codigoQr, TAMANO_PAGINA};
})();
