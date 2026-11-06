'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'destinos', titulo: () => 'Destinos'})) {
        return;
    }

    const $ = (id) => document.getElementById(id);
    const puedeEscribir = window.Ui.puedeEscribir();

    async function registrar() {
        const creado = await window.Ui.panel({
            titulo: 'Registrar destino',
            textoEnviar: 'Registrar destino',
            campos: [
                {id: 'nombreDestino', etiqueta: 'Nombre del destino', tipo: 'texto', requerido: true,
                    ayuda: 'Por ejemplo: Mercado Mayorista de Lima.'},
                {id: 'ciudadDestino', etiqueta: 'Ciudad', tipo: 'texto', requerido: true},
                {id: 'esLocal', etiqueta: '¿Está en tu misma zona?', tipo: 'select', requerido: true,
                    opciones: [{valor: 'false', texto: 'No, hay que transportarlo'}, {valor: 'true', texto: 'Sí, es local'}]}
            ],
            enviar: (v) => window.Api.pedir('/api/destinos', {
                metodo: 'POST',
                cuerpo: {nombreDestino: v.nombreDestino, ciudadDestino: v.ciudadDestino, esLocal: v.esLocal === 'true'}
            })
        });
        if (creado) {
            window.Ui.aviso('Destino registrado');
            cargar();
        }
    }

    async function cambiarPrecio(destino) {
        const guardado = await window.Ui.panel({
            titulo: 'Precio en ' + destino.nombreDestino,
            texto: 'Cuánto esperas que te paguen por kilo de este producto en este destino.',
            textoEnviar: 'Guardar precio',
            campos: [
                {id: 'productoLote', etiqueta: 'Producto', tipo: 'texto', requerido: true, marcador: 'Palta Hass',
                    ayuda: 'Escríbelo igual que en tus lotes.'},
                {id: 'precioPorKg', etiqueta: 'Precio por kilo (S/)', tipo: 'numero', requerido: true}
            ],
            enviar: (v) => window.Api.pedir('/api/destinos/' + destino.idDestinoVenta + '/precio', {
                metodo: 'PUT', cuerpo: {productoLote: v.productoLote, precioPorKg: v.precioPorKg}
            })
        });
        if (guardado) {
            window.Ui.aviso('Precio guardado');
        }
    }

    async function cambiarFlete(destino) {
        const guardado = await window.Ui.panel({
            titulo: 'Flete a ' + destino.nombreDestino,
            texto: 'Cuánto cuesta transportar cada kilo hasta este destino.',
            textoEnviar: 'Guardar flete',
            campos: [{id: 'costoFletePorKg', etiqueta: 'Flete por kilo (S/)', tipo: 'numero', requerido: true,
                permiteCero: true}],
            enviar: (v) => window.Api.pedir('/api/destinos/' + destino.idDestinoVenta + '/tarifa', {
                metodo: 'PUT', cuerpo: {costoFletePorKg: v.costoFletePorKg}
            })
        });
        if (guardado) {
            window.Ui.aviso('Flete guardado');
        }
    }

    function coordenada(maximo, nombre) {
        return (valor) => {
            const n = Number(valor.replace(',', '.'));
            return Number.isFinite(n) && Math.abs(n) <= maximo
                ? null : nombre + ' debe estar entre -' + maximo + ' y ' + maximo + '.';
        };
    }

    async function cambiarUbicacion(destino) {
        const guardado = await window.Ui.panel({
            titulo: 'Ubicación de ' + destino.nombreDestino,
            texto: 'Con la ubicación el comprador ve el avance del viaje y la hora estimada de llegada.',
            textoEnviar: 'Guardar ubicación',
            campos: [
                {id: 'latitud', etiqueta: 'Latitud', tipo: 'texto', requerido: true, marcador: '-12.0464',
                    validar: coordenada(90, 'La latitud'), ayuda: 'En el mapa, clic derecho sobre el lugar y copia los números.'},
                {id: 'longitud', etiqueta: 'Longitud', tipo: 'texto', requerido: true, marcador: '-77.0428',
                    validar: coordenada(180, 'La longitud')}
            ],
            enviar: (v) => window.Api.pedir('/api/destinos/' + destino.idDestinoVenta + '/coordenadas', {
                metodo: 'PUT',
                cuerpo: {latitud: Number(v.latitud.replace(',', '.')), longitud: Number(v.longitud.replace(',', '.'))}
            })
        });
        if (guardado) {
            window.Ui.aviso('Ubicación guardada');
        }
    }

    async function cargar() {
        window.Ui.errorDePagina('');
        try {
            const destinos = await window.Api.pedir('/api/destinos');
            window.Ui.tabla($('lista'), [
                {titulo: 'Destino', clase: 'largo', valor: (d) => d.nombreDestino},
                {titulo: 'Ciudad', clase: 'largo', valor: (d) => d.ciudadDestino},
                {titulo: 'Zona', valor: (d) => window.Ui.etiqueta(d.esLocal ? 'Local' : 'Con transporte',
                    d.esLocal ? '' : 'neutro')}
            ], destinos, {
                vacio: {titulo: 'Aún no hay destinos',
                    texto: puedeEscribir ? 'Registra el primer lugar donde vendes.' : 'El Dueño del negocio los registra.'},
                acciones: puedeEscribir ? (d) => [
                    window.Ui.boton('Precio', 'boton-secundario', () => cambiarPrecio(d)),
                    window.Ui.boton('Flete', 'boton-secundario', () => cambiarFlete(d)),
                    window.Ui.boton('Ubicación', 'boton-secundario', () => cambiarUbicacion(d))
                ] : null
            });
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    $('accion-principal').classList.toggle('oculto', !puedeEscribir);
    $('accion-principal').addEventListener('click', registrar);
    window.Ui.alReintentar(cargar);
    cargar();
})();
