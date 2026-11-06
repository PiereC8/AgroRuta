package pe.agroruta.infrastructure.config;

import java.time.Clock;
import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.agroruta.domain.model.SeriesComprobante;
import pe.agroruta.domain.port.out.ComprobantePort;
import pe.agroruta.domain.port.out.PagoPort;
import pe.agroruta.infrastructure.adapter.out.comprobante.ComprobanteSimulado;
import pe.agroruta.infrastructure.adapter.out.comprobante.NubefactComprobanteAdapter;
import pe.agroruta.infrastructure.adapter.out.http.ClienteHttp;
import pe.agroruta.infrastructure.adapter.out.pago.MercadoPagoPagoAdapter;
import pe.agroruta.infrastructure.adapter.out.pago.PasarelaSimulada;
import pe.agroruta.infrastructure.adapter.out.resiliencia.ComprobantePortConDisyuntor;
import pe.agroruta.infrastructure.adapter.out.resiliencia.Disyuntor;
import pe.agroruta.infrastructure.adapter.out.resiliencia.PagoPortConDisyuntor;

/**
 * Elige el proveedor real si hay credenciales y el simulador si no. Así el sistema funciona sin cuentas
 * y pasar a la pasarela real es solo poner las llaves en el .env.
 */
@Configuration
public class ProveedoresExternosConfig {

    private static final Logger REGISTRO = LoggerFactory.getLogger(ProveedoresExternosConfig.class);

    @Bean
    ClienteHttp clienteHttp() {
        return ClienteHttp.porDefecto();
    }

    @Bean
    ConfiguracionPasarela configuracionPasarela(
            @Value("${agroruta.pagos.mercadopago.token-acceso}") String tokenAcceso,
            @Value("${agroruta.pagos.mercadopago.llave-publica}") String llavePublica) {
        if (tokenAcceso.isBlank() || llavePublica.isBlank()) {
            return new ConfiguracionPasarela(ConfiguracionPasarela.SIMULADO, null);
        }
        return new ConfiguracionPasarela(ConfiguracionPasarela.MERCADOPAGO, llavePublica);
    }

    @Bean
    PagoPort pagoPort(ConfiguracionPasarela configuracion, ClienteHttp http, Clock reloj,
                      @Value("${agroruta.pagos.mercadopago.token-acceso}") String tokenAcceso,
                      @Value("${agroruta.pagos.mercadopago.url-base}") String urlBase,
                      @Value("${agroruta.resiliencia.fallos-para-abrir:5}") int fallosParaAbrir,
                      @Value("${agroruta.resiliencia.pausa-segundos:30}") int pausaSegundos) {
        if (ConfiguracionPasarela.MERCADOPAGO.equals(configuracion.modo())) {
            REGISTRO.info("Pasarela de pago: Mercado Pago ({})", urlBase);
            return new PagoPortConDisyuntor(new MercadoPagoPagoAdapter(http, urlBase, tokenAcceso),
                    new Disyuntor("La pasarela de pago", fallosParaAbrir, Duration.ofSeconds(pausaSegundos), reloj));
        }
        REGISTRO.warn("Pasarela de pago: SIMULADOR (no hay credenciales de Mercado Pago en el .env)");
        return new PasarelaSimulada();
    }

    @Bean
    ComprobantePort comprobantePort(ClienteHttp http, Clock reloj,
                                    @Value("${agroruta.comprobantes.nubefact.url}") String url,
                                    @Value("${agroruta.comprobantes.nubefact.token}") String token,
                                    @Value("${agroruta.comprobantes.nubefact.autorizacion}") String autorizacion,
                                    @Value("${agroruta.resiliencia.fallos-para-abrir:5}") int fallosParaAbrir,
                                    @Value("${agroruta.resiliencia.pausa-segundos:30}") int pausaSegundos) {
        if (url.isBlank() || token.isBlank()) {
            REGISTRO.warn("Comprobantes: SIMULADOR (no hay URL y token de Nubefact en el .env)");
            return new ComprobanteSimulado();
        }
        REGISTRO.info("Comprobantes: Nubefact");
        return new ComprobantePortConDisyuntor(new NubefactComprobanteAdapter(http, url, token,
                NubefactComprobanteAdapter.FormatoAutorizacion.valueOf(autorizacion)),
                new Disyuntor("El proveedor de comprobantes", fallosParaAbrir, Duration.ofSeconds(pausaSegundos), reloj));
    }

    @Bean
    SeriesComprobante seriesComprobante(@Value("${agroruta.comprobantes.serie-boleta}") String boleta,
                                        @Value("${agroruta.comprobantes.serie-factura}") String factura) {
        return new SeriesComprobante(boleta, factura);
    }
}
