'use strict';

/**
 * Formulario de la pasarela simulada (modo demostración). Hace lo que haría el formulario seguro de una pasarela
 * real: revisa la tarjeta en el navegador (algoritmo de Luhn, marca, vencimiento) y entrega solo un token con el
 * resultado, la marca y los últimos cuatro dígitos. El número completo y el código nunca se envían al servidor.
 */
window.PagoSimulado = (function () {
    const $ = (id) => document.getElementById(id);

    // Tarjetas de prueba: el número decide el resultado, como en los entornos de prueba de las pasarelas reales.
    const TARJETAS_PRUEBA = [
        {numero: '4242424242424242', texto: 'Visa aprobada', resultado: 'aprobada'},
        {numero: '5555555555554444', texto: 'Mastercard aprobada', resultado: 'aprobada'},
        {numero: '378282246310005', texto: 'American Express aprobada', resultado: 'aprobada'},
        {numero: '4000000000009995', texto: 'Sin fondos', resultado: 'sin_fondos'},
        {numero: '4000000000000069', texto: 'Tarjeta vencida', resultado: 'vencida'},
        {numero: '4000000000000119', texto: 'La pasarela no responde', resultado: 'sin_respuesta'}
    ];

    const NOMBRES_MARCA = {visa: 'Visa', mastercard: 'Mastercard', amex: 'American Express', diners: 'Diners'};

    function soloDigitos(texto) {
        return texto.replace(/\D/g, '');
    }

    /** Algoritmo de Luhn: detecta errores de tipeo en el número de la tarjeta. */
    function cumpleLuhn(numero) {
        let suma = 0;
        for (let i = 0; i < numero.length; i++) {
            let digito = Number(numero[numero.length - 1 - i]);
            if (i % 2 === 1) {
                digito *= 2;
                if (digito > 9) {
                    digito -= 9;
                }
            }
            suma += digito;
        }
        return suma % 10 === 0;
    }

    function marcaDe(numero) {
        if (/^4/.test(numero)) {
            return 'visa';
        }
        if (/^(5[1-5]|222[1-9]|22[3-9]|2[3-6]|27[01]|2720)/.test(numero)) {
            return 'mastercard';
        }
        if (/^3[47]/.test(numero)) {
            return 'amex';
        }
        if (/^3(0[0-5]|[68])/.test(numero)) {
            return 'diners';
        }
        return null;
    }

    function vencimientoValido(texto) {
        const m = /^(\d{2})\/(\d{2})$/.exec(texto);
        if (!m) {
            return false;
        }
        const mes = Number(m[1]);
        const anio = 2000 + Number(m[2]);
        const hoy = new Date();
        const vigente = anio > hoy.getFullYear() || (anio === hoy.getFullYear() && mes >= hoy.getMonth() + 1);
        return mes >= 1 && mes <= 12 && vigente;
    }

    function mostrarMarca() {
        const numero = soloDigitos($('sim-numero').value);
        const marca = marcaDe(numero);
        let texto = marca ? 'Tarjeta ' + NOMBRES_MARCA[marca] : '';
        if (numero.length >= 13 && !cumpleLuhn(numero)) {
            texto = 'El número no parece correcto, revísalo.';
        }
        $('sim-marca').textContent = texto;
    }

    function cambiarMedio() {
        const medio = $('sim-medio').value;
        $('sim-zona-tarjeta').classList.toggle('oculto', medio !== 'tarjeta');
        $('sim-zona-paypal').classList.toggle('oculto', medio !== 'paypal');
        $('sim-zona-pagoefectivo').classList.toggle('oculto', medio !== 'pagoefectivo');
    }

    let preparado = false;

    function preparar() {
        if (preparado) {
            return;
        }
        preparado = true;
        $('sim-medio').addEventListener('change', cambiarMedio);
        $('sim-numero').addEventListener('input', mostrarMarca);
    }

    function tokenizar() {
        if ($('sim-medio').value === 'paypal') {
            return {token: 'sim_aprobada_paypal'};
        }
        if ($('sim-medio').value === 'pagoefectivo') {
            return {token: 'sim_pagoefectivo'};
        }
        const numero = soloDigitos($('sim-numero').value);
        if (numero.length < 13 || numero.length > 19 || !cumpleLuhn(numero) || !marcaDe(numero)) {
            return {error: 'El número de la tarjeta no es válido. Revísalo.'};
        }
        if (!vencimientoValido($('sim-vence').value.trim())) {
            return {error: 'La fecha de vencimiento no es válida o ya pasó. Usa el formato MM/AA.'};
        }
        if (!/^\d{3,4}$/.test($('sim-codigo').value)) {
            return {error: 'Escribe el código de seguridad de la tarjeta (3 o 4 dígitos).'};
        }
        const prueba = TARJETAS_PRUEBA.find((t) => t.numero === numero);
        const resultado = prueba ? prueba.resultado : 'aprobada';
        return {token: 'sim_' + resultado + '_' + marcaDe(numero) + '_' + numero.slice(-4)};
    }

    return {preparar, tokenizar, cumpleLuhn, marcaDe};
})();
