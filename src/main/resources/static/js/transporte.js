// RFF-04: pantalla Transporte (transportistas y vehículos).
'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'transporte', titulo: () => 'Transporte'})) {
        return;
    }

    const $ = (id) => document.getElementById(id);
    const puedeEscribir = window.Ui.puedeEscribir();
    const vistaTransportistas = window.Ui.el('div');
    const vistaVehiculos = window.Ui.el('div');
    let transportistas = [];
    let vehiculos = [];
    let pestanaActual = 0;

    // RFF-23: cada pestaña tiene su barra de búsqueda y filtros, y su tabla debajo.
    const barraTransportistas = window.Ui.el('div');
    const listaTransportistas = window.Ui.el('div');
    const barraVehiculos = window.Ui.el('div');
    const listaVehiculos = window.Ui.el('div');
    vistaTransportistas.append(barraTransportistas, listaTransportistas);
    vistaVehiculos.append(barraVehiculos, listaVehiculos);

    const filtrosTransportistas = window.Ui.barraFiltros(barraTransportistas, [
        {id: 'texto', tipo: 'busqueda', etiqueta: 'Buscar', marcador: 'Nombre o teléfono…'},
        // Sin teléfono no se le puede mandar el enlace por WhatsApp al despachar.
        {id: 'telefono', tipo: 'select', etiqueta: 'Teléfono', opciones: [['SI', 'Con teléfono'], ['NO', 'Sin teléfono']]},
        {id: 'vehiculos', tipo: 'select', etiqueta: 'Vehículos', opciones: [['SI', 'Con vehículos'], ['NO', 'Sin vehículos']]}
    ], pintarTransportistas);

    const filtrosVehiculos = window.Ui.barraFiltros(barraVehiculos, [
        {id: 'texto', tipo: 'busqueda', etiqueta: 'Buscar', marcador: 'Placa o transportista…'},
        {id: 'transportista', tipo: 'select', etiqueta: 'Transportista'},
        // Para despachar un pedido grande hace falta un vehículo que lo cargue.
        {id: 'capacidad', tipo: 'select', etiqueta: 'Carga al menos',
            opciones: [['1000', '1,000 kg'], ['5000', '5,000 kg'], ['10000', '10,000 kg'], ['20000', '20,000 kg']]}
    ], pintarVehiculos);

    const vehiculosDe = (idTransportista) => vehiculos.filter((v) => v.idTransportista === idTransportista).length;

    function pintarTransportistas() {
        const f = filtrosTransportistas.valores();
        const visibles = transportistas.filter((t) => window.Ui.coincide(f.texto, t.nombreTransportista,
                t.telefonoTransportista)
            && (!f.telefono || (f.telefono === 'SI') === Boolean(t.telefonoTransportista))
            && (!f.vehiculos || (f.vehiculos === 'SI') === (vehiculosDe(t.idTransportista) > 0)));
        filtrosTransportistas.contar(visibles.length, transportistas.length);
        const filtrando = Object.values(f).some(Boolean);
        window.Ui.tabla(listaTransportistas, [
            {titulo: 'Nombre', clase: 'largo', valor: (t) => t.nombreTransportista},
            {titulo: 'Teléfono', valor: (t) => t.telefonoTransportista},
            {titulo: 'Vehículos', valor: (t) => vehiculosDe(t.idTransportista)}
        ], visibles, {
            acciones: puedeEscribir
                ? (t) => [window.Ui.boton('Editar', 'boton-secundario', () => registrarTransportista(t))]
                : undefined,
            vacio: filtrando
                ? {titulo: 'Ningún transportista coincide', texto: 'Cambia la búsqueda o pulsa «Limpiar».'}
                : {titulo: 'Aún no hay transportistas',
                    texto: puedeEscribir ? 'Registra al primero para poder asignarle vehículos.'
                        : 'El Dueño del negocio los registra.'}
        });
    }

    function pintarVehiculos() {
        const f = filtrosVehiculos.valores();
        const visibles = vehiculos.filter((v) => window.Ui.coincide(f.texto, v.placaVehiculo, nombreDe(v.idTransportista))
            && (!f.transportista || String(v.idTransportista) === f.transportista)
            && (!f.capacidad || Number(v.capacidadKg) >= Number(f.capacidad)));
        filtrosVehiculos.contar(visibles.length, vehiculos.length);
        const filtrando = Object.values(f).some(Boolean);
        window.Ui.tabla(listaVehiculos, [
            {titulo: 'Placa', valor: (v) => v.placaVehiculo},
            {titulo: 'Transportista', clase: 'largo', valor: (v) => nombreDe(v.idTransportista)},
            {titulo: 'Capacidad', valor: (v) => window.Ui.kilos(v.capacidadKg)}
        ], visibles, {
            vacio: filtrando
                ? {titulo: 'Ningún vehículo coincide', texto: 'Cambia la búsqueda o pulsa «Limpiar».'}
                : {titulo: 'Aún no hay vehículos',
                    texto: puedeEscribir ? 'Registra un vehículo para poder despachar pedidos.'
                        : 'El Dueño del negocio los registra.'}
        });
    }

    function telefonoValido(valor) {
        return !valor || /^[0-9+() -]{6,20}$/.test(valor) ? null : 'Escribe solo números, por ejemplo 987654321.';
    }

    function nombreDe(idTransportista) {
        const t = transportistas.find((x) => x.idTransportista === idTransportista);
        return t ? t.nombreTransportista : '—';
    }

    // Sin transportista: registrar uno nuevo. Con transportista: corregir su nombre o su teléfono.
    async function registrarTransportista(actual) {
        const editar = Boolean(actual && actual.idTransportista);
        const t = editar ? actual : {};
        const creado = await window.Ui.panel({
            titulo: editar ? 'Editar transportista' : 'Registrar transportista',
            textoEnviar: editar ? 'Guardar cambios' : 'Registrar transportista',
            campos: [
                {id: 'nombreTransportista', etiqueta: 'Nombre', tipo: 'texto', requerido: true,
                    valor: t.nombreTransportista},
                {id: 'telefonoTransportista', etiqueta: 'Teléfono', tipo: 'telefono', validar: telefonoValido,
                    valor: t.telefonoTransportista,
                    ayuda: 'A este número se le manda por WhatsApp el enlace para compartir su ubicación.'}
            ],
            enviar: (v) => window.Api.pedir('/api/transportistas' + (editar ? '/' + t.idTransportista : ''), {
                metodo: editar ? 'PUT' : 'POST',
                cuerpo: {nombreTransportista: v.nombreTransportista, telefonoTransportista: v.telefonoTransportista || null}
            })
        });
        if (creado) {
            window.Ui.aviso(editar ? 'Datos del transportista guardados' : 'Transportista registrado');
            cargar();
        }
    }

    async function registrarVehiculo() {
        if (transportistas.length === 0) {
            await window.Ui.detalle({
                titulo: 'Primero registra un transportista',
                texto: 'Cada vehículo pertenece a un transportista. Registra al transportista y vuelve aquí.',
                contenido: window.Ui.el('span')
            });
            return;
        }
        const creado = await window.Ui.panel({
            titulo: 'Registrar vehículo',
            textoEnviar: 'Registrar vehículo',
            campos: [
                {id: 'idTransportista', etiqueta: 'Transportista', tipo: 'select', requerido: true,
                    opciones: transportistas.map((t) => ({valor: t.idTransportista, texto: t.nombreTransportista}))},
                {id: 'placaVehiculo', etiqueta: 'Placa', tipo: 'texto', requerido: true, marcador: 'ABC-123',
                    validar: (v) => /^[A-Za-z0-9]{3}-?[A-Za-z0-9]{3,4}$/.test(v) ? null : 'La placa debe verse así: ABC-123.'},
                {id: 'capacidadKg', etiqueta: 'Capacidad en kilos', tipo: 'numero', requerido: true, maximo: 100000,
                    ayuda: 'Lo máximo que carga el vehículo.'}
            ],
            enviar: (v) => window.Api.pedir('/api/vehiculos', {
                metodo: 'POST',
                cuerpo: {idTransportista: Number(v.idTransportista), placaVehiculo: v.placaVehiculo,
                    capacidadKg: v.capacidadKg}
            })
        });
        if (creado) {
            window.Ui.aviso('Vehículo registrado');
            cargar();
        }
    }

    function actualizarBoton() {
        const boton = $('accion-principal');
        boton.classList.toggle('oculto', !puedeEscribir);
        boton.textContent = pestanaActual === 0 ? 'Registrar transportista' : 'Registrar vehículo';
    }

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            [transportistas, vehiculos] = await Promise.all([
                window.Api.pedir('/api/transportistas'), window.Api.pedir('/api/vehiculos')]);
            filtrosVehiculos.opciones('transportista', window.Ui.opcionesUnicas(vehiculos, (v) => v.idTransportista,
                (v) => nombreDe(v.idTransportista)));
            pintarTransportistas();
            pintarVehiculos();
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    window.Ui.pestanas($('pestanas'), [
        {texto: 'Transportistas', vista: vistaTransportistas, alActivar: () => { pestanaActual = 0; actualizarBoton(); }},
        {texto: 'Vehículos', vista: vistaVehiculos, alActivar: () => { pestanaActual = 1; actualizarBoton(); }}
    ]);
    $('accion-principal').addEventListener('click', () => pestanaActual === 0 ? registrarTransportista(null) : registrarVehiculo());
    window.Ui.alReintentar(cargar);
    cargar();
})();
