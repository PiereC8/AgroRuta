'use strict';

(function () {
    const REVISION_PENDIENTE_MS = 10000;
    const REVISIONES_MAXIMAS = 30;

    const $ = (id) => document.getElementById(id);
    const token = new URLSearchParams(location.hash.slice(1)).get('t');
    const dinero = new Intl.NumberFormat('es-PE', {style: 'currency', currency: 'PEN'});

    const estado = {datos: null, clave: null, enCurso: false, brickMontado: false, revisiones: 0, pagoConCodigo: false};

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

    async function enviarPago(datosPago) {
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
                body: JSON.stringify(datosPago)
            });
            const cuerpo = await respuesta.json().catch(() => ({}));
            if (!respuesta.ok) {
                mostrarResultado(cuerpo.error || 'No se pudo procesar el pago. Intenta de nuevo.', true);
                return false;
            }
            await alResultado(cuerpo);
            return true;
        } catch (e) {
            mostrarResultado('No pudimos conectar. Revisa tu señal e intenta de nuevo: no se cobrará dos veces.', true);
            return false;
        } finally {
            estado.enCurso = false;
            $('pagar').disabled = false;
        }
    }

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
            mostrar('zona-cip', estado.pagoConCodigo);
            resumen(estado.pagoConCodigo
                ? 'Tienes un código de pago pendiente. No pagues de nuevo con otro medio.'
                : 'Estamos confirmando tu pago. No pagues de nuevo.', 'aviso');
            revisarPendiente();
        }
    }

    async function simularPagoEnAgente() {
        $('simular-agente').disabled = true;
        try {
            const respuesta = await fetch('/api/publico/pago/simular-agente', {
                method: 'POST',
                headers: {'X-Token-Seguimiento': token}
            });
            const cuerpo = await respuesta.json().catch(() => ({}));
            if (!respuesta.ok) {
                mostrarResultado(cuerpo.error || 'No se pudo simular el pago. Intenta de nuevo.', false);
                return;
            }
            mostrar('zona-cip', false);
            // El comprobante se emite justo después de confirmar el pago: se espera un poco si aún no aparece.
            for (let intento = 0; intento < 5; intento++) {
                const datos = await cargar();
                if (datos) {
                    pintar(datos);
                }
                if (!datos || datos.comprobante) {
                    break;
                }
                await new Promise((listo) => setTimeout(listo, 2000));
            }
        } catch (e) {
            mostrarResultado('No pudimos conectar. Revisa tu señal e intenta de nuevo.', false);
        } finally {
            $('simular-agente').disabled = false;
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
                if (datos && datos.ultimoPago && datos.ultimoPago.estadoPago !== 'PENDIENTE') {
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
        if (comprobante.estado === 'ACEPTADO' || comprobante.estado === 'OBSERVADO') {
            const boton = document.createElement('button');
            boton.type = 'button';
            boton.className = 'boton boton-secundario';
            boton.textContent = 'Descargar PDF';
            boton.addEventListener('click', () => descargarPdf(boton));
            enlaces.append(boton, document.createTextNode('  '));
        }
        if (comprobante.enlaceXml && /^https:\/\//.test(comprobante.enlaceXml)) {
            const a = document.createElement('a');
            a.href = comprobante.enlaceXml;
            a.target = '_blank';
            a.rel = 'noopener noreferrer';
            a.textContent = 'Ver XML';
            enlaces.append(a);
        }
    }

    async function descargarPdf(boton) {
        boton.disabled = true;
        try {
            const respuesta = await fetch('/api/publico/pago/comprobante.pdf',
                {headers: {'X-Token-Seguimiento': token}, cache: 'no-store'});
            if (!respuesta.ok) {
                const cuerpo = await respuesta.json().catch(() => ({}));
                throw new Error(cuerpo.error || 'No se pudo descargar el comprobante. Intenta de nuevo.');
            }
            const nombre = /filename="?([^";]+)"?/.exec(respuesta.headers.get('Content-Disposition') || '');
            const url = URL.createObjectURL(await respuesta.blob());
            const enlace = document.createElement('a');
            enlace.href = url;
            enlace.download = nombre ? nombre[1] : 'comprobante.pdf';
            document.body.appendChild(enlace);
            enlace.click();
            enlace.remove();
            setTimeout(() => URL.revokeObjectURL(url), 1000);
        } catch (e) {
            $('comp-mensaje').textContent = e.message;
        } finally {
            boton.disabled = false;
        }
    }

    function pintar(datos) {
        $('dato-descripcion').textContent = datos.descripcion;
        $('dato-monto').textContent = dinero.format(datos.monto);
        $('pagar').textContent = 'Pagar ' + dinero.format(datos.monto);
        $('volver').href = '/seguimiento.html#t=' + encodeURIComponent(token);
        mostrar('zona-resultado', false);
        mostrar('zona-cip', false);
        mostrar('zona-comprobante', false);

        const ultimo = datos.ultimoPago;
        if (ultimo && ultimo.estadoPago === 'APROBADO') {
            mostrar('zona-pago', false);
            resumen('Este pedido ya está pagado. ¡Gracias!', 'ok');
            pintarComprobante(datos.comprobante);
        } else if (ultimo && ultimo.estadoPago === 'PENDIENTE' && ultimo.marcaTarjeta === 'PagoEfectivo') {
            mostrar('zona-pago', false);
            resumen('Tienes un código de pago pendiente. No pagues de nuevo con otro medio.', 'aviso');
            mostrarResultado(ultimo.mensaje, false);
            mostrar('zona-cip', datos.modo === 'SIMULADO');
            revisarPendiente();
        } else if (ultimo && ultimo.estadoPago === 'PENDIENTE') {
            mostrar('zona-pago', false);
            resumen('Estamos confirmando tu pago. No pagues de nuevo.', 'aviso');
            revisarPendiente();
        } else if (datos.pagable) {
            const simulado = datos.modo === 'SIMULADO';
            mostrar('zona-pago', true);
            mostrar('zona-simulada', simulado);
            if (simulado) {
                window.PagoSimulado.preparar();
            }
            mostrar('zona-tarjeta', !simulado);
            // Con Mercado Pago, el botón de pagar lo pone su propio formulario seguro.
            mostrar('pagar', simulado);
            resumen('Tu pedido fue entregado. Ya puedes pagarlo.', 'ok');
            if (ultimo && ultimo.estadoPago === 'RECHAZADO') {
                mostrarResultado(ultimo.mensaje, false);
            }
            if (!simulado) {
                montarFormularioSeguro(datos).catch(() => {
                    mostrarResultado('No se pudo cargar el formulario de pago. Revisa tu conexión y recarga la página.',
                        false);
                });
            }
        } else {
            mostrar('zona-pago', false);
            resumen(datos.estadoEnvio === 'ENTREGADO'
                ? 'No hay nada que pagar en este pedido.'
                : 'Todavía no puedes pagar: el pedido aún no fue entregado.', 'aviso');
        }
    }

    function pagarSimulado() {
        const email = $('simulado-email').value.trim();
        if (!email) {
            mostrarResultado('Escribe tu correo para enviarte el comprobante.', false);
            return;
        }
        const tokenizado = window.PagoSimulado.tokenizar();
        if (tokenizado.error) {
            mostrarResultado(tokenizado.error, false);
            return;
        }
        estado.pagoConCodigo = tokenizado.token === 'sim_pagoefectivo';
        enviarPago({tokenTarjeta: tokenizado.token, email});
    }

    function cargarSdk() {
        return new Promise((ok, mal) => {
            if (window.MercadoPago) {
                ok();
                return;
            }
            const script = document.createElement('script');
            script.src = 'https://sdk.mercadopago.com/js/v2';
            script.onload = () => ok();
            script.onerror = () => mal(new Error('No se pudo cargar Mercado Pago'));
            document.head.appendChild(script);
        });
    }

    /**
     * Los datos de la tarjeta se escriben en campos que Mercado Pago muestra dentro de su propio marco: esta página
     * nunca los ve. Al enviar, Mercado Pago entrega un token de un solo uso, el medio de pago y el correo.
     */
    async function montarFormularioSeguro(datos) {
        if (estado.brickMontado) {
            return;
        }
        estado.brickMontado = true;
        await cargarSdk();
        const mp = new window.MercadoPago(datos.llavePublica, {locale: 'es-PE'});
        await mp.bricks().create('cardPayment', 'brick-tarjeta', {
            initialization: {amount: Number(datos.monto)},
            callbacks: {
                onReady: () => {
                },
                onSubmit: (entrega) => new Promise((resolver, rechazar) => {
                    const formulario = entrega && entrega.formData ? entrega.formData : entrega;
                    if (estado.enCurso) {
                        rechazar();
                        return;
                    }
                    if (!estado.clave) {
                        estado.clave = nuevaClave();
                    }
                    enviarPago({
                        tokenTarjeta: formulario.token,
                        email: formulario.payer && formulario.payer.email,
                        metodoPago: formulario.payment_method_id,
                        emisor: formulario.issuer_id ? String(formulario.issuer_id) : null
                    }).then((respuestaDeNegocio) => respuestaDeNegocio ? resolver() : rechazar());
                }),
                onError: () => {
                    mostrarResultado('No pudimos validar la tarjeta. Revisa los datos e inténtalo de nuevo.', false);
                }
            }
        });
    }

    function alPulsarPagar() {
        if (estado.enCurso || !estado.datos || estado.datos.modo !== 'SIMULADO') {
            return;
        }
        if (!estado.clave) {
            estado.clave = nuevaClave();
        }
        pagarSimulado();
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
    $('simular-agente').addEventListener('click', simularPagoEnAgente);
    $('reintentar').addEventListener('click', () => {
        estado.clave = null;
        mostrar('zona-resultado', false);
        mostrar('zona-pago', true);
    });
    iniciar();
})();
