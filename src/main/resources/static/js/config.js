// Dónde está el backend. Si las páginas las sirve el mismo backend (VPS o local), queda vacío y se usa el mismo sitio.
// En Vercel, la API pasa por las reglas de vercel.json, pero el WebSocket del seguimiento va directo al backend.
(function () {
    'use strict';
    // Solo el sitio oficial usa este backend; una copia publicada en otro *.vercel.app no le manda tokens.
    const SITIO_VERCEL = 'agroruta.vercel.app';
    const BACKEND_VERCEL = 'https://agroruta-api.onrender.com';
    const base = location.hostname === SITIO_VERCEL ? BACKEND_VERCEL : location.origin;
    window.AgroRutaConfig = {
        urlWebSocket: base.replace(/^http/, 'ws') + '/ws'
    };
})();
