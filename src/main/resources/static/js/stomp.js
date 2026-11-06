'use strict';

/**
 * Cliente STOMP mínimo sobre WebSocket. Solo implementa lo que usa AgroRuta:
 * CONNECT, SUBSCRIBE, SEND y recibir MESSAGE y ERROR.
 */
class ClienteStomp {
    static TIEMPO_CONEXION_MS = 10000;

    constructor(url, cabecerasConexion, manejadores) {
        this.url = url;
        this.cabecerasConexion = cabecerasConexion;
        this.manejadores = manejadores;
        this.socket = null;
        this.conectado = false;
        this.contadorSuscripciones = 0;
    }

    conectar() {
        return new Promise((resolver, rechazar) => {
            let resuelta = false;
            const socket = new WebSocket(this.url);
            this.socket = socket;

            // Con señal muy mala el servidor puede no responder nunca: no se queda esperando para siempre.
            const reloj = setTimeout(() => {
                if (!resuelta) {
                    resuelta = true;
                    rechazar(new Error('La conexión tardó demasiado'));
                    socket.close();
                }
            }, ClienteStomp.TIEMPO_CONEXION_MS);

            socket.onopen = () => {
                this.#enviarTrama('CONNECT', Object.assign({'accept-version': '1.2', 'heart-beat': '0,0'},
                    this.cabecerasConexion), '');
            };
            socket.onmessage = (evento) => {
                const trama = ClienteStomp.#leerTrama(evento.data);
                if (!trama) {
                    return;
                }
                if (trama.comando === 'CONNECTED') {
                    clearTimeout(reloj);
                    this.conectado = true;
                    resuelta = true;
                    resolver();
                } else if (trama.comando === 'MESSAGE') {
                    let cuerpo = null;
                    try {
                        cuerpo = JSON.parse(trama.cuerpo);
                    } catch (e) {
                        return;
                    }
                    this.manejadores.alMensaje(trama.cabeceras.destination, cuerpo);
                } else if (trama.comando === 'ERROR') {
                    const motivo = trama.cabeceras.message || 'Error de conexión';
                    if (!resuelta) {
                        resuelta = true;
                        rechazar(new Error(motivo));
                    }
                    this.manejadores.alError(motivo);
                }
            };
            socket.onerror = () => {
                if (!resuelta) {
                    resuelta = true;
                    rechazar(new Error('No se pudo conectar'));
                }
            };
            socket.onclose = () => {
                const estabaConectado = this.conectado;
                this.conectado = false;
                if (!resuelta) {
                    resuelta = true;
                    rechazar(new Error('Conexión cerrada'));
                }
                this.manejadores.alCerrar(estabaConectado);
            };
        });
    }

    suscribir(destino) {
        this.contadorSuscripciones += 1;
        this.#enviarTrama('SUBSCRIBE', {id: 'sub-' + this.contadorSuscripciones, destination: destino}, '');
    }

    enviar(destino, objeto) {
        if (!this.conectado) {
            throw new Error('Sin conexión');
        }
        this.#enviarTrama('SEND', {destination: destino, 'content-type': 'application/json'},
            JSON.stringify(objeto));
    }

    cerrar() {
        if (this.socket && this.socket.readyState <= 1) {
            this.socket.close();
        }
    }

    #enviarTrama(comando, cabeceras, cuerpo) {
        let texto = comando + '\n';
        for (const [nombre, valor] of Object.entries(cabeceras)) {
            texto += nombre + ':' + valor + '\n';
        }
        this.socket.send(texto + '\n' + cuerpo + '\u0000');
    }

    static #leerTrama(datos) {
        if (typeof datos !== 'string' || datos.trim() === '') {
            return null;
        }
        const fin = datos.indexOf('\u0000');
        const texto = fin >= 0 ? datos.slice(0, fin) : datos;
        const separador = texto.indexOf('\n\n');
        const encabezado = separador >= 0 ? texto.slice(0, separador) : texto;
        const cuerpo = separador >= 0 ? texto.slice(separador + 2) : '';
        const lineas = encabezado.split('\n');
        const cabeceras = {};
        for (const linea of lineas.slice(1)) {
            const dosPuntos = linea.indexOf(':');
            if (dosPuntos > 0) {
                const nombre = linea.slice(0, dosPuntos);
                if (!(nombre in cabeceras)) {
                    cabeceras[nombre] = linea.slice(dosPuntos + 1);
                }
            }
        }
        return {comando: lineas[0].trim(), cabeceras, cuerpo};
    }
}
