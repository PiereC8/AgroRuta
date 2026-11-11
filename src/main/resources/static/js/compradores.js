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

    // Sin comprador: registrar uno nuevo. Con comprador: corregir sus datos (por ejemplo, agregarle el DNI o RUC).
    async function registrar(actual) {
        const editar = Boolean(actual && actual.idComprador);
        const c = editar ? actual : {};
        const creado = await window.Ui.panel({
            titulo: editar ? 'Editar comprador' : 'Registrar comprador',
            textoEnviar: editar ? 'Guardar cambios' : 'Registrar comprador',
            campos: [
                {id: 'nombreComprador', etiqueta: 'Nombre', tipo: 'texto', requerido: true, valor: c.nombreComprador},
                {id: 'tipoComprador', etiqueta: 'Tipo de comprador', tipo: 'select', requerido: true,
                    valor: c.tipoComprador, opciones: Object.entries(TIPOS).map(([valor, texto]) => ({valor, texto}))},
                {id: 'documentoComprador', etiqueta: 'Documento', tipo: 'texto', validar: documentoValido,
                    valor: c.documentoComprador,
                    ayuda: 'DNI (8 dígitos) o RUC (11 dígitos). Con RUC se emite factura; sin documento, boleta. '
                        + 'Para cobrar S/ 700 o más hace falta el documento.'},
                {id: 'telefonoComprador', etiqueta: 'Teléfono', tipo: 'telefono', validar: telefonoValido,
                    valor: c.telefonoComprador},
                {id: 'ciudadComprador', etiqueta: 'Ciudad', tipo: 'texto', requerido: true, valor: c.ciudadComprador}
            ],
            enviar: (valores) => window.Api.pedir('/api/compradores' + (editar ? '/' + c.idComprador : ''), {
                metodo: editar ? 'PUT' : 'POST',
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
            window.Ui.aviso(editar ? 'Datos del comprador guardados' : 'Comprador registrado');
            cargar();
        }
    }

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            const compradores = await window.Api.pedir('/api/compradores');
            window.Ui.tabla($('lista'), [
                {titulo: 'Nombre', clase: 'largo', valor: (c) => c.nombreComprador},
                {titulo: 'Tipo', valor: (c) => TIPOS[c.tipoComprador] || c.tipoComprador},
                {titulo: 'Documento', valor: documentoConTipo},
                {titulo: 'Ciudad', clase: 'largo', valor: (c) => c.ciudadComprador},
                {titulo: 'Teléfono', valor: (c) => c.telefonoComprador}
            ], compradores, {
                acciones: (c) => [window.Ui.boton('Editar', 'boton-secundario', () => registrar(c))],
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
