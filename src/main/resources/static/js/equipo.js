'use strict';

(function () {
    if (!window.Estructura.montar({activo: 'equipo', titulo: () => 'Equipo'})) {
        return;
    }
    if (!window.Ui.puedeEscribir()) {
        window.location.replace('/inicio.html');
        return;
    }

    const $ = (id) => document.getElementById(id);
    const ROLES = {DUENO: 'Dueño', OPERADOR: 'Operador'};
    const TIPOS = {
        ENTREGA_CON_PERDIDA: 'Entrega con pérdida', MERMA_ALTA: 'Merma alta', PAGO_RECIBIDO: 'Pago recibido',
        COMPROBANTE_RECHAZADO: 'Comprobante rechazado', COMPROBANTE_ATASCADO: 'Comprobante pendiente',
        COBRO_VENCIDO: 'Cobro vencido', ENVIO_DETENIDO: 'Envío detenido', GPS_DESACTIVADO: 'GPS apagado',
        ENVIO_RETRASADO: 'Envío con retraso', ENVIO_LLEGADO: 'Envío llegó', RESUMEN_SEMANAL: 'Resumen semanal', PRUEBA: 'Prueba'
    };
    const ESTADOS = {
        PENDIENTE: ['Por enviar', 'espera'], ENVIADA: ['Enviado', ''], FALLIDA: ['No se pudo enviar', 'error']
    };

    const validarCorreo = (valor) => /^[^@\s]+@[^@\s]+\.[^@\s]+$/.test(valor) ? null : 'Escribe un correo válido, por ejemplo nombre@empresa.pe.';
    const validarCorreoOpcional = (valor) => valor === '' ? null : validarCorreo(valor);

    function avisosDe(m) {
        if (!m.correo) {
            return window.Ui.etiqueta('Sin correo', 'neutro');
        }
        return m.avisosActivos ? window.Ui.etiqueta('Activos', '') : window.Ui.etiqueta('Apagados', 'neutro');
    }

    async function agregarOperador() {
        const creado = await window.Ui.panel({
            titulo: 'Agregar operador',
            texto: 'El operador registra lotes, pedidos, envíos y cobros, pero no ve la ganancia, el flete ni los reportes.',
            textoEnviar: 'Agregar operador',
            campos: [
                {id: 'nombreUsuario', etiqueta: 'Usuario', tipo: 'texto', requerido: true,
                    ayuda: 'De 3 a 50 caracteres: letras, números, punto, guion o guion bajo.'},
                {id: 'clave', etiqueta: 'Clave inicial', tipo: 'clave', requerido: true,
                    validar: (v) => v.length >= 8 && v.length <= 72 ? null : 'La clave debe tener entre 8 y 72 caracteres.',
                    ayuda: 'Entrégasela en persona; no se vuelve a mostrar.'},
                {id: 'correo', etiqueta: 'Correo para los avisos', tipo: 'correo', requerido: false,
                    validar: validarCorreoOpcional}
            ],
            enviar: (v) => window.Api.pedir('/api/equipo', {
                metodo: 'POST',
                cuerpo: {nombreUsuario: v.nombreUsuario, clave: v.clave, correo: v.correo || null}
            })
        });
        if (creado) {
            window.Ui.aviso('Operador agregado');
            cargarPersonas();
        }
    }

    async function cambiarCorreo(m) {
        const guardado = await window.Ui.panel({
            titulo: 'Correo de ' + m.nombreUsuario,
            texto: 'A este correo llegan los avisos que le corresponden a su rol.',
            textoEnviar: 'Guardar',
            campos: [
                {id: 'correo', etiqueta: 'Correo', tipo: 'correo', requerido: false, valor: m.correo || '',
                    validar: validarCorreoOpcional},
                {id: 'avisosActivos', etiqueta: '¿Recibe avisos?', tipo: 'select', requerido: true,
                    valor: String(m.avisosActivos),
                    opciones: [{valor: 'true', texto: 'Sí, enviarle avisos'}, {valor: 'false', texto: 'No, apagar los avisos'}]}
            ],
            enviar: (v) => window.Api.pedir('/api/equipo/' + m.idUsuario + '/correo', {
                metodo: 'PUT', cuerpo: {correo: v.correo || null, avisosActivos: v.avisosActivos === 'true'}
            })
        });
        if (guardado) {
            window.Ui.aviso('Correo guardado');
            cargarPersonas();
        }
    }

    async function restablecerClave(m) {
        const listo = await window.Ui.panel({
            titulo: 'Contraseña de ' + m.nombreUsuario,
            texto: 'Para cuando el operador olvidó su contraseña. También le quita el bloqueo por intentos fallidos. '
                + 'Entrégasela en persona y pídele que la cambie con el botón «Contraseña».',
            textoEnviar: 'Guardar contraseña',
            campos: [
                {id: 'claveNueva', etiqueta: 'Contraseña nueva', tipo: 'clave', requerido: true,
                    validar: (v) => v.length >= 8 && v.length <= 72 ? null : 'La clave debe tener entre 8 y 72 caracteres.'},
                {id: 'repetir', etiqueta: 'Repite la contraseña nueva', tipo: 'clave', requerido: true,
                    validar: (v, todos) => v !== todos.claveNueva ? 'No coincide con la contraseña nueva.' : null}
            ],
            enviar: (v) => window.Api.pedir('/api/equipo/' + m.idUsuario + '/clave', {
                metodo: 'PUT', cuerpo: {claveNueva: v.claveNueva}
            }).then(() => true)
        });
        if (listo) {
            window.Ui.aviso('Contraseña cambiada para ' + m.nombreUsuario);
        }
    }

    async function cambiarActivo(m) {
        const activar = !m.usuarioActivo;
        const acepto = await window.Ui.confirmar({
            titulo: (activar ? 'Activar a ' : 'Desactivar a ') + m.nombreUsuario,
            texto: activar ? 'Podrá volver a entrar al sistema.' : 'Ya no podrá entrar al sistema ni recibirá avisos.',
            textoAceptar: activar ? 'Activar' : 'Desactivar',
            enviar: () => window.Api.pedir('/api/equipo/' + m.idUsuario + '/activo', {
                metodo: 'PUT', cuerpo: {activo: activar}
            }).then(() => true)
        });
        if (acepto) {
            window.Ui.aviso(activar ? 'Persona activada' : 'Persona desactivada');
            cargarPersonas();
        }
    }

    async function cargarPersonas() {
        window.Ui.errorDePagina('');
        try {
            const personas = await window.Api.pedir('/api/equipo');
            const yo = (window.Api.usuario() || {}).nombreUsuario;
            window.Ui.tabla($('lista-personas'), [
                {titulo: 'Usuario', clase: 'largo', valor: (m) => m.nombreUsuario},
                {titulo: 'Rol', valor: (m) => ROLES[m.rolUsuario] || m.rolUsuario},
                {titulo: 'Correo', clase: 'largo', valor: (m) => m.correo},
                {titulo: 'Avisos', valor: avisosDe},
                {titulo: 'Estado', valor: (m) => window.Ui.etiqueta(m.usuarioActivo ? 'Activo' : 'Desactivado',
                    m.usuarioActivo ? '' : 'neutro')}
            ], personas, {
                vacio: {titulo: 'Aún no hay personas'},
                acciones: (m) => {
                    const botones = [window.Ui.boton('Correo', 'boton-secundario', () => cambiarCorreo(m))];
                    if (m.rolUsuario === 'OPERADOR') {
                        botones.push(window.Ui.boton('Clave', 'boton-secundario', () => restablecerClave(m)));
                    }
                    if (m.nombreUsuario !== yo) {
                        botones.push(window.Ui.boton(m.usuarioActivo ? 'Desactivar' : 'Activar', 'boton-secundario',
                            () => cambiarActivo(m)));
                    }
                    return botones;
                }
            });
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    async function cargarCorreos() {
        window.Ui.errorDePagina('');
        try {
            const correos = await window.Api.pedir('/api/notificaciones?limite=50');
            window.Ui.tabla($('lista-correos'), [
                {titulo: 'Fecha', valor: (c) => window.Ui.fecha(c.fechaCreacion)},
                {titulo: 'Aviso', valor: (c) => TIPOS[c.tipoAviso] || c.tipoAviso},
                {titulo: 'Para', clase: 'largo', valor: (c) => c.correoDestino},
                {titulo: 'Asunto', clase: 'largo', valor: (c) => c.asunto},
                {titulo: 'Estado', valor: (c) => {
                    const [texto, tono] = ESTADOS[c.estado] || [c.estado, 'neutro'];
                    return window.Ui.etiqueta(texto, tono);
                }},
                {titulo: 'Detalle', clase: 'largo', valor: (c) => c.ultimoError}
            ], correos, {
                vacio: {titulo: 'Todavía no se envió ningún correo',
                    texto: 'Aparecerán aquí cuando pase algo que merezca un aviso.'}
            });
        } catch (error) {
            window.Ui.errorDePagina(error.message);
        }
    }

    async function probarCorreo() {
        const enviado = await window.Ui.panel({
            titulo: 'Enviar correo de prueba',
            texto: 'Comprueba que los correos salen y llegan. Si el correo del sistema no está configurado, la prueba es simulada.',
            textoEnviar: 'Enviar prueba',
            campos: [{id: 'correo', etiqueta: 'Enviar a', tipo: 'correo', requerido: true, validar: validarCorreo}],
            enviar: (v) => window.Api.pedir('/api/notificaciones/prueba', {metodo: 'POST', cuerpo: {correo: v.correo}})
                .then(() => true)
        });
        if (enviado) {
            window.Ui.aviso('Correo de prueba enviado');
            cargarCorreos();
        }
    }

    window.Ui.pestanas($('pestanas'), [
        {texto: 'Personas', vista: $('personas'), alActivar: cargarPersonas},
        {texto: 'Correos enviados', vista: $('correos'), alActivar: cargarCorreos}
    ]);
    $('accion-principal').addEventListener('click', agregarOperador);
    $('probar-correo').addEventListener('click', probarCorreo);
    window.Ui.alReintentar(cargarPersonas);
})();
