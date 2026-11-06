'use strict';

window.Estructura = (function () {
    const NS = 'http://www.w3.org/2000/svg';

    // Íconos de 24 px (trazo simple). Cada uno es una lista de trazados.
    const ICONOS = {
        inicio: ['M3 11l9-8 9 8', 'M5 10v10h14V10', 'M10 20v-6h4v6'],
        lotes: ['M3 7l9-4 9 4-9 4-9-4z', 'M3 7v10l9 4 9-4V7', 'M12 11v10'],
        pedidos: ['M9 4h6v3H9z', 'M7 5H5v16h14V5h-2', 'M9 12h6', 'M9 16h6'],
        envios: ['M2 6h12v10H2z', 'M14 10h4l3 3v3h-7', 'M6 19a2 2 0 100-4 2 2 0 000 4z',
            'M17 19a2 2 0 100-4 2 2 0 000 4z'],
        compradores: ['M9 11a3.5 3.5 0 100-7 3.5 3.5 0 000 7z', 'M2 20c0-3.5 3-6 7-6s7 2.5 7 6',
            'M17 4.5a3 3 0 010 6', 'M19 14c2 .7 3 2.6 3 5'],
        destinos: ['M12 21s7-6 7-12a7 7 0 10-14 0c0 6 7 12 7 12z', 'M12 11.5a2.5 2.5 0 100-5 2.5 2.5 0 000 5z'],
        transporte: ['M4 17V8l3-4h10l3 4v9', 'M4 12h16', 'M7 20v-3', 'M17 20v-3'],
        pagos: ['M2 6h20v13H2z', 'M2 10h20', 'M6 15h4']
    };

    const SECCIONES = [
        {id: 'inicio', texto: 'Inicio', ruta: '/inicio.html'},
        {id: 'lotes', texto: 'Lotes', ruta: '/lotes.html'},
        {id: 'pedidos', texto: 'Pedidos', ruta: '/pedidos.html'},
        {id: 'envios', texto: 'Envíos', ruta: '/envios.html'},
        {id: 'compradores', texto: 'Compradores', ruta: '/compradores.html'},
        {id: 'destinos', texto: 'Destinos', ruta: '/destinos.html'},
        {id: 'transporte', texto: 'Transporte', ruta: '/transporte.html'},
        {id: 'pagos', texto: 'Pagos', ruta: '/pagos.html'}
    ];

    const ROLES = {DUENO: 'Dueño', OPERADOR: 'Operador'};

    function icono(nombre) {
        const svg = document.createElementNS(NS, 'svg');
        svg.setAttribute('viewBox', '0 0 24 24');
        svg.setAttribute('class', 'menu__icono');
        svg.setAttribute('aria-hidden', 'true');
        for (const d of ICONOS[nombre]) {
            const trazo = document.createElementNS(NS, 'path');
            trazo.setAttribute('d', d);
            svg.appendChild(trazo);
        }
        return svg;
    }

    function elemento(etiqueta, clase, texto) {
        const e = document.createElement(etiqueta);
        if (clase) {
            e.className = clase;
        }
        if (texto !== undefined) {
            e.textContent = texto;
        }
        return e;
    }

    function saludo() {
        const hora = new Date().getHours();
        return hora < 12 ? 'Buenos días' : hora < 19 ? 'Buenas tardes' : 'Buenas noches';
    }

    function dibujarMenu(destino, activo) {
        destino.replaceChildren();
        destino.className = 'menu';
        destino.setAttribute('aria-label', 'Menú principal');

        const marca = elemento('div', 'menu__marca');
        const logo = document.createElement('img');
        logo.src = '/img/logo.png';
        logo.alt = 'AgroRuta';
        marca.appendChild(logo);

        const lista = elemento('ul', 'menu__lista');
        for (const seccion of SECCIONES) {
            const item = document.createElement('li');
            const pronto = seccion.ruta === null;
            const enlace = document.createElement(pronto ? 'span' : 'a');
            enlace.className = 'menu__enlace' + (pronto ? ' menu__enlace--pronto' : '');
            enlace.setAttribute('aria-label', pronto ? seccion.texto + ' (próximamente)' : seccion.texto);
            if (!pronto) {
                enlace.title = seccion.texto;
                enlace.href = seccion.ruta;
                if (seccion.id === activo) {
                    enlace.setAttribute('aria-current', 'page');
                }
            }
            enlace.appendChild(icono(seccion.id));
            enlace.appendChild(elemento('span', 'menu__texto', seccion.texto));
            if (pronto) {
                enlace.appendChild(elemento('span', 'menu__pronto', 'Pronto'));
                enlace.title = seccion.texto + ': próximamente';
            }
            item.appendChild(enlace);
            lista.appendChild(item);
        }
        destino.append(marca, lista);
    }

    function dibujarEncabezado(destino, titulo) {
        const datos = window.Api.usuario();
        destino.replaceChildren();
        destino.className = 'encabezado';

        const izquierda = document.createElement('div');
        izquierda.appendChild(elemento('h1', null, titulo(datos)));
        if (datos && datos.identificadorNegocio) {
            izquierda.appendChild(elemento('p', 'suave', datos.identificadorNegocio));
        }

        const sesion = elemento('div', 'encabezado__sesion');
        const usuario = elemento('div', 'encabezado__usuario');
        usuario.appendChild(elemento('strong', null, datos ? datos.nombreUsuario : ''));
        usuario.appendChild(elemento('span', 'suave', datos ? (ROLES[datos.rolUsuario] || datos.rolUsuario) : ''));
        const salir = elemento('button', 'boton boton-secundario', 'Cerrar sesión');
        salir.type = 'button';
        salir.addEventListener('click', window.Api.cerrarSesion);
        sesion.append(usuario, salir);

        destino.append(izquierda, sesion);
    }

    function montar(opciones) {
        if (!window.Api.haySesion()) {
            window.location.replace('/login.html');
            return false;
        }
        dibujarMenu(document.getElementById('menu'), opciones.activo);
        dibujarEncabezado(document.getElementById('encabezado'), opciones.titulo
            || (() => saludo()));
        return true;
    }

    return {montar, saludo};
})();
