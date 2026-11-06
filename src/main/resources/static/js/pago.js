'use strict';

(function () {
    const REVISION_PENDIENTE_MS = 10000;
    const REVISIONES_MAXIMAS = 30;

    const $ = (id) => document.getElementById(id);
    const token = new URLSearchParams(location.hash.slice(1)).get('t');
    const dinero = new Intl.NumberFormat('es-PE', {style: 'currency', currency: 'PEN'});

    const estado = {datos: null, clave: null, enCurso: false, culqiListo: false, revisiones: 0};

    const ESTADOS_COMPROBANTE = {
        PENDIENTE: 'Pendiente: lo estamos enviando a SUNAT',
        ACEPTADO: 'Aceptado por SUNAT',
        OBSERVADO: 'Emitido con observaciones',
        RECHAZADO: 'No se pudo emitir, nos pondremos en contacto'
    };

    function mostrar(id, visible) {
        $(id).classList.toggle('oculto', !visible);
    }

    function enlaceInvalido() {
        mostrar('contenido', false);
        mostrar('enlace-invalido', true);
    }

    function resumen(texto, clase) {
        const caja = $('resumen');
        caja.textContent = texto;
        caja.className = 'mensaje ' + (clase || '');
    }

    function nuevaClave() {
        return crypto.randomUUID().replace(/-/g, '');
    }

    // ---------- Servidor ----------

    async function cargar() {
        const respuesta = await fetch('/api/publico/pago', {headers: {'X-Token-Seguimiento': token}, cache: 'no-store'});
        if (respuesta.status === 404 || respuesta.status === 401) {
            enlaceInvalido();
            return null;
        }
        if (!respuesta.ok) {
            throw new Error('Error ' + respuesta.status);
        }
        estado.datos = await respuesta.json();
        return estado.datos;
    }

    async function enviarPago(tokenTarjeta, email) {
        estado.enCurso = true;
        $('pagar').disabled = true;
        try {
            const respuesta = await fetch('/api/publico/pago', {
                method: 'POST',
                headers: {
                    'Content-Type': 'application/json',
                    'X-Token-Seguimiento': token,
                    'Idempotency-Key': estado.clave
                },
                body: JSON.stringify({tokenTarjeta, email})
            });
            const cuerpo = await respuesta.json().catch(() => ({}));
            if (!respuesta.ok) {
                mostrarResultado(cuerpo.error || 'No se pudo procesar el pago. Intenta de nuevo.', true);
                return;
            }
            await alResultado(cuerpo);
        } catch (e) {
            mostrarResultado('No pudimos conectar. Revisa tu señal e intenta de nuevo: no se cobrará dos veces.', true);
        } finally {
            estado.enCurso = false;
            $('pagar').disabled = false;
        }
    }

    // ---------- Resultado ----------

    function mostrarResultado(texto, permiteReintento) {
        mostrar('zona-resultado', true);
        $('resultado-mensaje').textContent = texto;
        mostrar('reintentar', permiteReintento);
    }

    async function alResultado(resultado) {
        if (resultado.estadoPago === 'APROBADO') {
            mostrar('zona-pago', false);
            mostrarResultado(resultado.mensaje, false);
            resumen('Pedido pagado. ¡Gracias!', 'ok');
            pintarComprobante(resultado.comprobante);
        } else if (resultado.estadoPago === 'RECHAZADO') {
            // Se explica el motivo y se permite reintentar de inmediato, con otra clave de operación.
            estado.clave = null;
            mostrarResultado(resultado.mensaje, true);
        } else {
            mostrarResultado(resultado.mensaje, false);
            mostrar('zona-pago', false);
            revisarPendiente();
        }
    }

    function revisarPendiente() {
        if (estado.revisiones >= REVISIONES_MAXIMAS) {
            return;
        }
        estado.revisiones += 1;
        setTimeout(async () => {
            try {
                const datos = await cargar();
                if (datos && datos.ultimoPago && datos.ultimoPago.estadoPago === 'APROBADO') {
                    pintar(datos);
                    return;
                }
                if (datos && datos.ultimoPago && datos.ultimoPago.estadoPago === 'RECHAZADO') {
                    pintar(datos);
                    return;
                }
            } catch (e) {
                // Se reintenta en la siguiente vuelta.
            }
            revisarPendiente();
        }, REVISION_PENDIENTE_MS);
    }

    function pintarComprobante(comprobante) {
        if (!comprobante) {
            mostrar('zona-comprobante', false);
            return;
        }
        mostrar('zona-comprobante', true);
        $('comp-numero').textContent = (comprobante.tipo === 'FACTURA' ? 'Factura ' : 'Boleta ') + comprobante.numero;
        $('comp-estado').textContent = ESTADOS_COMPROBANTE[comprobante.estado] || comprobante.estado;
        $('comp-mensaje').textContent = comprobante.mensaje || '';
        const enlaces = $('comp-enlaces');
        enlaces.replaceChildren();
        for (const [texto, url] of [['Ver PDF', comprobante.enlacePdf], ['Ver XML', comprobante.enlaceXml]]) {
            if (url && /^https:\/\//.test(url)) {
                const a = document.createElement('a');
                a.href = url;
                a.target = '_blank';
                a.rel = 'noopener noreferrer';
                a.textContent = texto;
                enlaces.append(a, document.createTextNode('  '));
            }
        }
    }

    // ---------- Pantalla ----------

    function pintar(datos) {
        $('dato-descripcion').textContent = datos.descripcion;
        $('dato-monto').textContent = dinero.format(datos.monto);
        $('pagar').textContent = 'Pagar ' + dinero.format(datos.monto);
        $('volver').href = '/seguimiento.html#t=' + encodeURIComponent(token);
        mostrar('zona-resultado', false);
        mostrar('zona-comprobante', false);

        const ultimo = datos.ultimoPago;
        if (ultimo && ultimo.estadoPago === 'APROBADO') {
            mostrar('zona-pago', false);
            resumen('Este pedido ya está pagado. ¡Gracias!', 'ok');
            pintarComprobante(datos.comprobante);
        } else if (ultimo && ultimo.estadoPago === 'PENDIENTE') {
            mostrar('zona-pago', false);
            resumen('Estamos confirmando tu pago. No pagues de nuevo.', 'aviso');
            revisarPendiente();
        } else if (datos.pagable) {
            const simulado = datos.modo === 'SIMULADO';
            mostrar('zona-pago', true);
            mostrar('zona-simulada', simulado);
            mostrar('nota-culqi', !simulado);
            resumen('Tu pedido fue entregado. Ya puedes pagarlo.', 'ok');
            if (ultimo && ultimo.estadoPago === 'RECHAZADO') {
                mostrarResultado(ultimo.mensaje, false);
            }
        } else {
            mostrar('zona-pago', false);
            resumen(datos.estadoEnvio === 'ENTREGADO'
                ? 'No hay nada que pagar en este pedido.'
                : 'Todavía no puedes pagar: el pedido aún no fue entregado.', 'aviso');
        }
    }

    // ---------- Pago ----------

    function pagarSimulado() {
        const email = $('simulado-email').value.trim();
        if (!email) {
            mostrarResultado('Escribe tu correo para enviarte el comprobante.', false);
            return;
        }
        enviarPago($('simulado-resultado').value, email);
    }

    function cargarCulqi(datos) {
        return new Promise((ok, mal) => {
            if (estado.culqiListo) {
                ok();
                return;
            }
            const script = document.createElement('script');
            script.src = 'https://checkout.culqi.com/js/v4';
            script.onload = () => {
                window.Culqi.publicKey = datos.llavePublica;
                const opciones = {
                    title: 'AgroRuta',
                    currency: datos.moneda,
                    amount: Math.round(datos.monto * 100)
                };
                if (datos.rsaId && datos.rsaPublica) {
                    opciones.xculqirsaid = datos.rsaId;
                    opciones.rsapublickey = datos.rsaPublica;
                }
                window.Culqi.settings(opciones);
                window.Culqi.options({lang: 'auto', installments: false, paymentMethods: {tarjeta: true}});
                // Culqi llama a esta función cuando el comprador terminó de escribir su tarjeta.
                window.culqi = alTokenCulqi;
                estado.culqiListo = true;
                ok();
            };
            script.onerror = () => mal(new Error('No se pudo cargar Culqi'));
            document.head.appendChild(script);
        });
    }

    function alTokenCulqi() {
        const culqi = window.Culqi;
        if (culqi.token) {
            const idToken = culqi.token.id;
            const email = culqi.token.email;
            culqi.close();
            enviarPago(idToken, email);
        } else if (culqi.error) {
            mostrarResultado(culqi.error.user_message || 'No pudimos validar la tarjeta. Revisa los datos.', true);
        }
    }

    async function pagarConCulqi() {
        try {
            await cargarCulqi(estado.datos);
            window.Culqi.open();
        } catch (e) {
            mostrarResultado('No se pudo abrir la ventana de pago. Revisa tu conexión e intenta de nuevo.', true);
        }
    }

    function alPulsarPagar() {
        if (estado.enCurso || !estado.datos) {
            return;
        }
        if (!estado.clave) {
            estado.clave = nuevaClave();
        }
        if (estado.datos.modo === 'SIMULADO') {
            pagarSimulado();
        } else {
            pagarConCulqi();
        }
    }

    async function iniciar() {
        if (!token) {
            enlaceInvalido();
            return;
        }
        mostrar('contenido', true);
        try {
            const datos = await cargar();
            if (datos) {
                pintar(datos);
            }
        } catch (e) {
            resumen('No se pudo cargar. Reintentando...', 'aviso');
            setTimeout(iniciar, 5000);
        }
    }

    $('pagar').addEventListener('click', alPulsarPagar);
    $('reintentar').addEventListener('click', () => {
        estado.clave = null;
        mostrar('zona-resultado', false);
        mostrar('zona-pago', true);
    });
    iniciar();
})();
