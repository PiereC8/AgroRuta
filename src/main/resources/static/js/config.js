// Dónde está el backend. Si las páginas las sirve el mismo backend (VPS o local), queda vacío y se usa el mismo sitio.
// En Vercel, la API pasa por las reglas de vercel.json, pero el WebSocket del seguimiento va directo al backend.
(function () {
    'use strict';
    const BACKEND_VERCEL = 'https://agroruta-api.onrender.com';
    const enVercel = location.hostname.endsWith('.vercel.app');
    const base = enVercel ? BACKEND_VERCEL : location.origin;
    window.AgroRutaConfig = {
        urlWebSocket: base.replace(/^http/, 'ws') + '/ws'
    };
})();
