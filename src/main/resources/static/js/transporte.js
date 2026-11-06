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
    let pestanaActual = 0;

    function telefonoValido(valor) {
        return !valor || /^[0-9+() -]{6,20}$/.test(valor) ? null : 'Escribe solo números, por ejemplo 987654321.';
    }

    function nombreDe(idTransportista) {
        const t = transportistas.find((x) => x.idTransportista === idTransportista);
        return t ? t.nombreTransportista : '—';
    }

    async function registrarTransportista() {
        const creado = await window.Ui.panel({
            titulo: 'Registrar transportista',
            textoEnviar: 'Registrar transportista',
            campos: [
                {id: 'nombreTransportista', etiqueta: 'Nombre', tipo: 'texto', requerido: true},
                {id: 'telefonoTransportista', etiqueta: 'Teléfono', tipo: 'telefono', validar: telefonoValido}
            ],
            enviar: (v) => window.Api.pedir('/api/transportistas', {
                metodo: 'POST',
                cuerpo: {nombreTransportista: v.nombreTransportista, telefonoTransportista: v.telefonoTransportista || null}
            })
        });
        if (creado) {
            window.Ui.aviso('Transportista registrado');
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
            const [listaTransportistas, vehiculos] = await Promise.all([
                window.Api.pedir('/api/transportistas'), window.Api.pedir('/api/vehiculos')]);
            transportistas = listaTransportistas;
            window.Ui.tabla(vistaTransportistas, [
                {titulo: 'Nombre', valor: (t) => t.nombreTransportista},
                {titulo: 'Teléfono', valor: (t) => t.telefonoTransportista},
                {titulo: 'Vehículos', valor: (t) => vehiculos.filter((v) => v.idTransportista === t.idTransportista).length}
            ], transportistas, {
                vacio: {titulo: 'Aún no hay transportistas',
                    texto: puedeEscribir ? 'Registra al primero para poder asignarle vehículos.'
                        : 'El Dueño del negocio los registra.'}
            });
            window.Ui.tabla(vistaVehiculos, [
                {titulo: 'Placa', valor: (v) => v.placaVehiculo},
                {titulo: 'Transportista', valor: (v) => nombreDe(v.idTransportista)},
                {titulo: 'Capacidad', valor: (v) => window.Ui.kilos(v.capacidadKg)}
            ], vehiculos, {
                vacio: {titulo: 'Aún no hay vehículos',
                    texto: puedeEscribir ? 'Registra un vehículo para poder despachar pedidos.'
                        : 'El Dueño del negocio los registra.'}
            });
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    window.Ui.pestanas($('pestanas'), [
        {texto: 'Transportistas', vista: vistaTransportistas, alActivar: () => { pestanaActual = 0; actualizarBoton(); }},
        {texto: 'Vehículos', vista: vistaVehiculos, alActivar: () => { pestanaActual = 1; actualizarBoton(); }}
    ]);
    $('accion-principal').addEventListener('click', () => pestanaActual === 0 ? registrarTransportista() : registrarVehiculo());
    window.Ui.alReintentar(cargar);
    cargar();
})();
