package pe.agroruta.infrastructure.adapter.out.comprobante;

import java.time.Clock;
import java.util.function.Supplier;

import pe.agroruta.domain.model.DatosComprobante;
import pe.agroruta.domain.model.ResultadoEmision;
import pe.agroruta.domain.model.TipoComprobante;
import pe.agroruta.domain.port.out.ComprobantePort;
import pe.agroruta.infrastructure.adapter.out.http.ClienteHttp;

/** Emite con los datos fiscales del negocio actual (ver {@link EmisorPorNegocio}). */
public class ComprobantePorNegocio implements ComprobantePort {

    private final ClienteHttp http;
    private final String urlBase;
    private final Supplier<DatosEmisor> emisor;
    private final Clock reloj;

    public ComprobantePorNegocio(ClienteHttp http, String urlBase, Supplier<DatosEmisor> emisor, Clock reloj) {
        this.http = http;
        this.urlBase = urlBase;
        this.emisor = emisor;
        this.reloj = reloj;
    }

    @Override
    public ResultadoEmision emitir(DatosComprobante datos) {
        return adaptador().emitir(datos);
    }

    @Override
    public ResultadoEmision consultar(TipoComprobante tipo, String serie, int numero) {
        return adaptador().consultar(tipo, serie, numero);
    }

    private FacturacionDirectaAdapter adaptador() {
        return new FacturacionDirectaAdapter(http, urlBase, emisor.get(), reloj);
    }
}
