'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'compradores', titulo: () => 'Compradores'})) {
        return;
    }

    const $ = (id) => document.getElementById(id);
    const TIPOS = {MAYORISTA: 'Mayorista', MERCADO: 'Mercado', EMPRESA: 'Empresa'};

    function documentoValido(valor) {
        return !valor || /^(\d{8}|\d{11})$/.test(valor)
            ? null : 'Escribe un DNI de 8 dígitos o un RUC de 11 dígitos, sin espacios.';
    }

    function telefonoValido(valor) {
        return !valor || /^[0-9+() -]{6,20}$/.test(valor) ? null : 'Escribe solo números, por ejemplo 987654321.';
    }

    function documentoConTipo(c) {
        if (!c.documentoComprador) {
            return '—';
        }
        return (c.documentoComprador.length === 11 ? 'RUC ' : 'DNI ') + c.documentoComprador;
    }

    async function registrar() {
        const creado = await window.Ui.panel({
            titulo: 'Registrar comprador',
            textoEnviar: 'Registrar comprador',
            campos: [
                {id: 'nombreComprador', etiqueta: 'Nombre', tipo: 'texto', requerido: true},
                {id: 'tipoComprador', etiqueta: 'Tipo de comprador', tipo: 'select', requerido: true,
                    opciones: Object.entries(TIPOS).map(([valor, texto]) => ({valor, texto}))},
                {id: 'documentoComprador', etiqueta: 'Documento', tipo: 'texto', validar: documentoValido,
                    ayuda: 'DNI (8 dígitos) o RUC (11 dígitos). Con RUC se emite factura; sin documento, boleta.'},
                {id: 'telefonoComprador', etiqueta: 'Teléfono', tipo: 'telefono', validar: telefonoValido},
                {id: 'ciudadComprador', etiqueta: 'Ciudad', tipo: 'texto', requerido: true}
            ],
            enviar: (valores) => window.Api.pedir('/api/compradores', {
                metodo: 'POST',
                cuerpo: {
                    nombreComprador: valores.nombreComprador,
                    tipoComprador: valores.tipoComprador,
                    documentoComprador: valores.documentoComprador || null,
                    telefonoComprador: valores.telefonoComprador || null,
                    ciudadComprador: valores.ciudadComprador
                }
            })
        });
        if (creado) {
            window.Ui.aviso('Comprador registrado');
            cargar();
        }
    }

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            const compradores = await window.Api.pedir('/api/compradores');
            window.Ui.tabla($('lista'), [
                {titulo: 'Nombre', valor: (c) => c.nombreComprador},
                {titulo: 'Tipo', valor: (c) => TIPOS[c.tipoComprador] || c.tipoComprador},
                {titulo: 'Documento', valor: documentoConTipo},
                {titulo: 'Ciudad', valor: (c) => c.ciudadComprador},
                {titulo: 'Teléfono', valor: (c) => c.telefonoComprador}
            ], compradores, {
                vacio: {titulo: 'Aún no hay compradores',
                    texto: 'Registra a quien le vendes para poder crear sus pedidos.'}
            });
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    $('accion-principal').classList.remove('oculto');
    $('accion-principal').addEventListener('click', registrar);
    window.Ui.alReintentar(cargar);
    cargar();
})();
