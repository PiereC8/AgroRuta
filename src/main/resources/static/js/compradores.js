// RFF-04: pantalla Compradores (lista, registrar y editar).
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
                // RF-07 / RFF-04: correo opcional; si se llena, al despachar le llega su enlace (RF-14).
                {id: 'correoComprador', etiqueta: 'Correo', tipo: 'correo', valor: c.correoComprador,
                    ayuda: 'Opcional. Al despachar su pedido le llega solo el enlace para seguirlo en el mapa.'},
                {id: 'ciudadComprador', etiqueta: 'Ciudad', tipo: 'texto', requerido: true, valor: c.ciudadComprador}
            ],
            enviar: (valores) => window.Api.pedir('/api/compradores' + (editar ? '/' + c.idComprador : ''), {
                metodo: editar ? 'PUT' : 'POST',
                cuerpo: {
                    nombreComprador: valores.nombreComprador,
                    tipoComprador: valores.tipoComprador,
                    documentoComprador: valores.documentoComprador || null,
                    telefonoComprador: valores.telefonoComprador || null,
                    correoComprador: valores.correoComprador || null,
                    ciudadComprador: valores.ciudadComprador
                }
            })
        });
        if (creado) {
            window.Ui.aviso(editar ? 'Datos del comprador guardados' : 'Comprador registrado');
            cargar();
        }
    }

    // RFF-23: búsqueda y filtros de compradores, en el navegador.
    let compradores = [];
    const filtros = window.Ui.barraFiltros($('barra-filtros'), [
        {id: 'texto', tipo: 'busqueda', etiqueta: 'Buscar', marcador: 'Nombre, DNI/RUC, teléfono o correo…'},
        {id: 'tipo', tipo: 'select', etiqueta: 'Tipo', opciones: Object.entries(TIPOS)},
        {id: 'ciudad', tipo: 'select', etiqueta: 'Ciudad', todos: 'Todas'},
        // Sin DNI o RUC no se le puede cobrar S/ 700 o más (comprobante).
        {id: 'documento', tipo: 'select', etiqueta: 'Documento',
            opciones: [['DNI', 'Con DNI'], ['RUC', 'Con RUC'], ['NINGUNO', 'Sin documento']]},
        // Sin correo no le llega solo el enlace de seguimiento al despachar.
        {id: 'correo', tipo: 'select', etiqueta: 'Correo', opciones: [['SI', 'Con correo'], ['NO', 'Sin correo']]}
    ], pintar);

    function tipoDocumento(c) {
        if (!c.documentoComprador) {
            return 'NINGUNO';
        }
        return c.documentoComprador.length === 11 ? 'RUC' : 'DNI';
    }

    function pintar() {
        const f = filtros.valores();
        const visibles = compradores.filter((c) => window.Ui.coincide(f.texto, c.nombreComprador, c.documentoComprador,
                c.telefonoComprador, c.correoComprador, c.ciudadComprador)
            && (!f.tipo || c.tipoComprador === f.tipo)
            && (!f.ciudad || c.ciudadComprador === f.ciudad)
            && (!f.documento || tipoDocumento(c) === f.documento)
            && (!f.correo || (f.correo === 'SI') === Boolean(c.correoComprador)));
        filtros.contar(visibles.length, compradores.length);
        const filtrando = Object.values(f).some(Boolean);
        window.Ui.tabla($('lista'), [
            {titulo: 'Nombre', clase: 'largo', valor: (c) => c.nombreComprador},
            {titulo: 'Tipo', valor: (c) => TIPOS[c.tipoComprador] || c.tipoComprador},
            {titulo: 'Documento', valor: documentoConTipo},
            {titulo: 'Ciudad', clase: 'largo', valor: (c) => c.ciudadComprador},
            {titulo: 'Teléfono', valor: (c) => c.telefonoComprador},
            {titulo: 'Correo', clase: 'largo', valor: (c) => c.correoComprador || '—'}
        ], visibles, {
            acciones: (c) => [window.Ui.boton('Editar', 'boton-secundario', () => registrar(c))],
            vacio: filtrando
                ? {titulo: 'Ningún comprador coincide', texto: 'Cambia la búsqueda o pulsa «Limpiar».'}
                : {titulo: 'Aún no hay compradores', texto: 'Registra a quien le vendes para poder crear sus pedidos.'}
        });
    }

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            compradores = await window.Api.pedir('/api/compradores');
            filtros.opciones('ciudad', window.Ui.opcionesUnicas(compradores, (c) => c.ciudadComprador));
            pintar();
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    $('accion-principal').classList.remove('oculto');
    $('accion-principal').addEventListener('click', registrar);
    window.Ui.alReintentar(cargar);
    cargar();
})();
