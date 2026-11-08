package pe.agroruta.infrastructure.config;

import java.time.Clock;
import java.time.Duration;
import java.util.Properties;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.mail.javamail.JavaMailSenderImpl;

import pe.agroruta.domain.model.SeriesComprobante;
import pe.agroruta.domain.port.out.ComprobantePort;
import pe.agroruta.domain.port.out.EnvioCorreoPort;
import pe.agroruta.domain.port.out.PagoPort;
import pe.agroruta.domain.port.out.RepresentacionComprobantePort;
import pe.agroruta.infrastructure.adapter.out.comprobante.ComprobantePdf;
import pe.agroruta.infrastructure.adapter.out.comprobante.ComprobanteSimulado;
import pe.agroruta.infrastructure.adapter.out.comprobante.DatosEmisor;
import pe.agroruta.infrastructure.adapter.out.comprobante.FacturacionDirectaAdapter;
import pe.agroruta.infrastructure.adapter.out.correo.CorreoSimuladoAdapter;
import pe.agroruta.infrastructure.adapter.out.correo.CorreoSmtpAdapter;
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

    /**
     * Con COMPROBANTES_PROVEEDOR=facturaciondirecta los comprobantes van al ambiente beta de SUNAT por esa API (sin
     * validez tributaria); si no, se simulan. Para producción haría falta RUC, usuario SOL secundario y certificado.
     */
    @Bean
    DatosEmisor datosEmisor(@Value("${agroruta.comprobantes.emisor.ruc}") String ruc,
                            @Value("${agroruta.comprobantes.emisor.razon-social}") String razonSocial,
                            @Value("${agroruta.comprobantes.emisor.nombre-comercial}") String nombreComercial,
                            @Value("${agroruta.comprobantes.emisor.domicilio-fiscal}") String domicilio,
                            @Value("${agroruta.comprobantes.emisor.ubigeo}") String ubigeo,
                            @Value("${agroruta.comprobantes.emisor.distrito}") String distrito,
                            @Value("${agroruta.comprobantes.emisor.provincia}") String provincia,
                            @Value("${agroruta.comprobantes.emisor.departamento}") String departamento) {
        return new DatosEmisor(ruc, razonSocial, nombreComercial, domicilio, ubigeo, distrito, provincia, departamento);
    }

    @Bean
    ComprobantePort comprobantePort(ClienteHttp http, Clock reloj, DatosEmisor emisor,
                                    @Value("${agroruta.comprobantes.proveedor}") String proveedor,
                                    @Value("${agroruta.comprobantes.facturaciondirecta.url-base}") String urlBase,
                                    @Value("${agroruta.resiliencia.fallos-para-abrir:5}") int fallosParaAbrir,
                                    @Value("${agroruta.resiliencia.pausa-segundos:30}") int pausaSegundos) {
        if (usaSunatBeta(proveedor)) {
            REGISTRO.info("Comprobantes: SUNAT BETA por facturaciondirecta.com (RUC emisor {})", emisor.ruc());
            return new ComprobantePortConDisyuntor(new FacturacionDirectaAdapter(http, urlBase, emisor, reloj),
                    new Disyuntor("El proveedor de comprobantes", fallosParaAbrir, Duration.ofSeconds(pausaSegundos),
                            reloj));
        }
        REGISTRO.warn("Comprobantes: SIMULADOR (COMPROBANTES_PROVEEDOR no es facturaciondirecta)");
        return new ComprobanteSimulado();
    }

    @Bean
    RepresentacionComprobantePort representacionComprobantePort(DatosEmisor emisor,
            @Value("${agroruta.comprobantes.proveedor}") String proveedor) {
        String aviso = usaSunatBeta(proveedor)
                ? "Emitido en el ambiente de pruebas (beta) de SUNAT: no tiene validez tributaria."
                : "Comprobante simulado para demostración: no fue enviado a SUNAT y no tiene validez tributaria.";
        return new ComprobantePdf(emisor, aviso);
    }

    private static boolean usaSunatBeta(String proveedor) {
        return "facturaciondirecta".equalsIgnoreCase(proveedor.trim());
    }

    @Bean
    SeriesComprobante seriesComprobante(@Value("${agroruta.comprobantes.serie-boleta}") String boleta,
                                        @Value("${agroruta.comprobantes.serie-factura}") String factura) {
        return new SeriesComprobante(boleta, factura);
    }

    @Bean
    EnvioCorreoPort envioCorreoPort(@Value("${agroruta.correo.servidor}") String servidor,
                                    @Value("${agroruta.correo.puerto}") int puerto,
                                    @Value("${agroruta.correo.usuario}") String usuario,
                                    @Value("${agroruta.correo.clave}") String clave,
                                    @Value("${agroruta.correo.remitente}") String remitente) {
        if (usuario.isBlank() || clave.isBlank()) {
            REGISTRO.warn("Correo: SIMULADOR (no hay usuario y clave de aplicación del correo en el .env)");
            return new CorreoSimuladoAdapter();
        }
        JavaMailSenderImpl envio = new JavaMailSenderImpl();
        envio.setHost(servidor);
        envio.setPort(puerto);
        envio.setUsername(usuario);
        envio.setPassword(clave);
        envio.setDefaultEncoding("UTF-8");
        Properties propiedades = envio.getJavaMailProperties();
        propiedades.put("mail.smtp.auth", "true");
        propiedades.put("mail.smtp.starttls.enable", "true");
        propiedades.put("mail.smtp.connectiontimeout", "10000");
        propiedades.put("mail.smtp.timeout", "10000");
        propiedades.put("mail.smtp.writetimeout", "10000");
        REGISTRO.info("Correo: SMTP en {}:{}", servidor, puerto);
        return new CorreoSmtpAdapter(envio, remitente.isBlank() ? usuario : remitente);
    }
}
