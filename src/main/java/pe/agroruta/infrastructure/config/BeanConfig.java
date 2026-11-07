package pe.agroruta.infrastructure.config;

import java.time.Clock;
import pe.agroruta.domain.port.out.EquipoRepositoryPort;
import pe.agroruta.domain.port.in.AdministrarEquipoUseCase;
import pe.agroruta.application.service.EquipoService;
import pe.agroruta.infrastructure.adapter.out.reporte.ExportadorPdf;
import pe.agroruta.infrastructure.adapter.out.reporte.ExportadorCsv;
import pe.agroruta.domain.port.in.ExportarReporteUseCase;
import pe.agroruta.domain.model.Formato;
import pe.agroruta.application.service.ExportarReporteService;
import java.util.Map;
import java.time.ZoneId;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import pe.agroruta.application.service.AltaNegocioService;
import pe.agroruta.application.service.ConfirmarPagoService;
import pe.agroruta.application.service.ConsultarPagosService;
import pe.agroruta.application.service.EmitirComprobanteService;
import pe.agroruta.application.service.PagarPedidoService;
import pe.agroruta.domain.model.SeriesComprobante;
import pe.agroruta.domain.port.in.ConfirmarPagoUseCase;
import pe.agroruta.domain.port.in.ConsultarPagosUseCase;
import pe.agroruta.domain.port.in.EmitirComprobanteUseCase;
import pe.agroruta.domain.port.in.PagarPedidoUseCase;
import pe.agroruta.domain.port.out.ComprobantePort;
import pe.agroruta.domain.port.out.ComprobanteRepositoryPort;
import pe.agroruta.domain.port.out.CorrelativoPort;
import pe.agroruta.domain.port.out.EjecutorPorNegocioPort;
import pe.agroruta.domain.port.out.PagoPort;
import pe.agroruta.domain.port.out.PagoRepositoryPort;
import pe.agroruta.application.service.AutenticarEnlaceService;
import pe.agroruta.application.service.ConsultarSeguimientoService;
import pe.agroruta.application.service.GenerarEnlacesEnvioService;
import pe.agroruta.application.service.SeguimientoConductorService;
import pe.agroruta.domain.port.in.AutenticarEnlaceUseCase;
import pe.agroruta.domain.port.in.ConsultarSeguimientoUseCase;
import pe.agroruta.domain.port.in.GenerarEnlacesEnvioUseCase;
import pe.agroruta.domain.port.in.SeguimientoConductorUseCase;
import pe.agroruta.domain.port.out.EnlaceEnvioRepositoryPort;
import pe.agroruta.domain.port.out.GeneradorTokenPort;
import pe.agroruta.domain.port.out.PublicadorSeguimientoPort;
import pe.agroruta.domain.port.out.SeguimientoRepositoryPort;
import pe.agroruta.application.service.ConsultarLiquidacionesService;
import pe.agroruta.application.service.ConsultarEstadoPagoService;
import pe.agroruta.application.service.ConsultarPanelService;
import pe.agroruta.application.service.ConsultarReportesService;
import pe.agroruta.domain.port.in.ConsultarEstadoPagoUseCase;
import pe.agroruta.domain.port.in.ConsultarPanelUseCase;
import pe.agroruta.domain.port.in.ConsultarReportesUseCase;
import pe.agroruta.domain.port.out.PanelRepositoryPort;
import pe.agroruta.domain.port.out.ReporteRepositoryPort;
import pe.agroruta.application.service.RegistrarEntregaService;
import pe.agroruta.domain.port.in.ConsultarLiquidacionesUseCase;
import pe.agroruta.domain.port.in.RegistrarEntregaUseCase;
import pe.agroruta.domain.port.out.LiquidacionRepositoryPort;
import pe.agroruta.application.service.AdministrarCompradoresService;
import pe.agroruta.application.service.AdministrarTransporteService;
import pe.agroruta.application.service.DespacharEnvioService;
import pe.agroruta.application.service.GestionarPedidosService;
import pe.agroruta.domain.port.in.AdministrarCompradoresUseCase;
import pe.agroruta.domain.port.in.AdministrarTransporteUseCase;
import pe.agroruta.domain.port.in.DespacharEnvioUseCase;
import pe.agroruta.domain.port.in.GestionarPedidosUseCase;
import pe.agroruta.domain.port.out.CompradorRepositoryPort;
import pe.agroruta.domain.port.out.EnvioRepositoryPort;
import pe.agroruta.domain.port.out.PedidoRepositoryPort;
import pe.agroruta.domain.port.out.TransaccionPort;
import pe.agroruta.domain.port.out.TransporteRepositoryPort;
import pe.agroruta.application.service.AdministrarDestinosService;
import pe.agroruta.application.service.ConsultarLotesService;
import pe.agroruta.application.service.CotizarFleteService;
import pe.agroruta.domain.port.in.AdministrarDestinosUseCase;
import pe.agroruta.domain.port.in.CotizarFleteUseCase;
import pe.agroruta.domain.port.out.DestinoRepositoryPort;
import pe.agroruta.domain.port.out.PreciosDestinoPort;
import pe.agroruta.domain.port.out.UbicacionDestinoPort;
import pe.agroruta.domain.port.out.TarifaPort;
import pe.agroruta.application.service.IniciarSesionService;
import pe.agroruta.application.service.RegistrarLoteService;
import pe.agroruta.domain.port.in.AltaNegocioUseCase;
import pe.agroruta.domain.port.in.ConsultarLotesUseCase;
import pe.agroruta.domain.port.in.IniciarSesionUseCase;
import pe.agroruta.domain.port.in.RegistrarLoteUseCase;
import pe.agroruta.domain.port.out.LoteRepositoryPort;
import pe.agroruta.domain.port.out.BaseNegocioPort;
import pe.agroruta.domain.port.out.EmisorTokenPort;
import pe.agroruta.domain.port.out.HashClavePort;
import pe.agroruta.domain.port.out.NegocioRepositoryPort;
import pe.agroruta.domain.port.out.UsuarioRepositoryPort;

@Configuration
public class BeanConfig {

    @Bean
    Clock reloj() {
        return Clock.system(ZoneId.of("America/Lima"));
    }

    @Bean
    AltaNegocioUseCase altaNegocioUseCase(NegocioRepositoryPort negocios, BaseNegocioPort bases,
                                          UsuarioRepositoryPort usuarios, HashClavePort hashes) {
        return new AltaNegocioService(negocios, bases, usuarios, hashes);
    }

    @Bean
    RegistrarLoteUseCase registrarLoteUseCase(LoteRepositoryPort lotes, Clock reloj) {
        return new RegistrarLoteService(lotes, reloj);
    }

    @Bean
    ConsultarLotesUseCase consultarLotesUseCase(LoteRepositoryPort lotes) {
        return new ConsultarLotesService(lotes);
    }

    @Bean
    CotizarFleteUseCase cotizarFleteUseCase(LoteRepositoryPort lotes, DestinoRepositoryPort destinos,
                                            PreciosDestinoPort precios, TarifaPort tarifas) {
        return new CotizarFleteService(lotes, destinos, precios, tarifas);
    }

    @Bean
    AdministrarDestinosUseCase administrarDestinosUseCase(DestinoRepositoryPort destinos,
                                                          PreciosDestinoPort precios,
                                                          UbicacionDestinoPort ubicaciones) {
        return new AdministrarDestinosService(destinos, precios, ubicaciones);
    }

    @Bean
    AdministrarCompradoresUseCase administrarCompradoresUseCase(CompradorRepositoryPort compradores) {
        return new AdministrarCompradoresService(compradores);
    }

    @Bean
    AdministrarTransporteUseCase administrarTransporteUseCase(TransporteRepositoryPort transporte) {
        return new AdministrarTransporteService(transporte);
    }

    @Bean
    GestionarPedidosUseCase gestionarPedidosUseCase(PedidoRepositoryPort pedidos, CompradorRepositoryPort compradores,
                                                    LoteRepositoryPort lotes, DestinoRepositoryPort destinos,
                                                    Clock reloj) {
        return new GestionarPedidosService(pedidos, compradores, lotes, destinos, reloj);
    }

    @Bean
    DespacharEnvioUseCase despacharEnvioUseCase(PedidoRepositoryPort pedidos, LoteRepositoryPort lotes,
                                                TransporteRepositoryPort transporte, EnvioRepositoryPort envios,
                                                TransaccionPort transaccion, Clock reloj) {
        return new DespacharEnvioService(pedidos, lotes, transporte, envios, transaccion, reloj);
    }

    @Bean
    RegistrarEntregaUseCase registrarEntregaUseCase(EnvioRepositoryPort envios, PedidoRepositoryPort pedidos,
                                                    LoteRepositoryPort lotes, DestinoRepositoryPort destinos,
                                                    TarifaPort tarifas, LiquidacionRepositoryPort liquidaciones,
                                                    TransaccionPort transaccion, Clock reloj) {
        return new RegistrarEntregaService(envios, pedidos, lotes, destinos, tarifas, liquidaciones, transaccion,
                reloj);
    }

    @Bean
    ConsultarLiquidacionesUseCase consultarLiquidacionesUseCase(LiquidacionRepositoryPort liquidaciones) {
        return new ConsultarLiquidacionesService(liquidaciones);
    }

    @Bean
    ConsultarPanelUseCase consultarPanelUseCase(PanelRepositoryPort panel, Clock reloj) {
        return new ConsultarPanelService(panel, reloj);
    }

    @Bean
    ConsultarReportesUseCase consultarReportesUseCase(ReporteRepositoryPort reportes,
                                                      DestinoRepositoryPort destinos) {
        return new ConsultarReportesService(reportes, destinos);
    }

    @Bean
    ExportarReporteUseCase exportarReporteUseCase(ConsultarReportesUseCase consultas, NegocioRepositoryPort negocios,
                                                  Clock reloj) {
        return new ExportarReporteService(consultas, negocios,
                Map.of(Formato.CSV, new ExportadorCsv(), Formato.PDF, new ExportadorPdf()), reloj);
    }

    @Bean
    AdministrarEquipoUseCase administrarEquipoUseCase(EquipoRepositoryPort equipo, HashClavePort hashes) {
        return new EquipoService(equipo, hashes);
    }

    @Bean
    GenerarEnlacesEnvioUseCase generarEnlacesEnvioUseCase(EnvioRepositoryPort envios,
                                                          EnlaceEnvioRepositoryPort enlaces,
                                                          GeneradorTokenPort generador, Clock reloj) {
        return new GenerarEnlacesEnvioService(envios, enlaces, generador, reloj);
    }

    @Bean
    AutenticarEnlaceUseCase autenticarEnlaceUseCase(EnlaceEnvioRepositoryPort enlaces, GeneradorTokenPort generador,
                                                    Clock reloj) {
        return new AutenticarEnlaceService(enlaces, generador, reloj);
    }

    @Bean
    SeguimientoConductorUseCase seguimientoConductorUseCase(EnvioRepositoryPort envios, PedidoRepositoryPort pedidos,
                                                            UbicacionDestinoPort ubicaciones,
                                                            SeguimientoRepositoryPort seguimiento,
                                                            PublicadorSeguimientoPort publicador, Clock reloj) {
        return new SeguimientoConductorService(envios, pedidos, ubicaciones, seguimiento, publicador, reloj);
    }

    @Bean
    ConsultarSeguimientoUseCase consultarSeguimientoUseCase(EnvioRepositoryPort envios, PedidoRepositoryPort pedidos,
                                                            DestinoRepositoryPort destinos,
                                                            UbicacionDestinoPort ubicaciones,
                                                            SeguimientoRepositoryPort seguimiento, Clock reloj) {
        return new ConsultarSeguimientoService(envios, pedidos, destinos, ubicaciones, seguimiento, reloj);
    }

    @Bean
    EmitirComprobanteUseCase emitirComprobanteUseCase(PagoRepositoryPort pagos, ComprobanteRepositoryPort comprobantes,
                                                      LiquidacionRepositoryPort liquidaciones,
                                                      PedidoRepositoryPort pedidos, CompradorRepositoryPort compradores,
                                                      LoteRepositoryPort lotes, CorrelativoPort correlativos,
                                                      ComprobantePort emisor, SeriesComprobante series, Clock reloj) {
        return new EmitirComprobanteService(pagos, comprobantes, liquidaciones, pedidos, compradores, lotes,
                correlativos, emisor, series, reloj);
    }

    @Bean
    PagarPedidoUseCase pagarPedidoUseCase(EnvioRepositoryPort envios, PedidoRepositoryPort pedidos,
                                          LiquidacionRepositoryPort liquidaciones, CompradorRepositoryPort compradores,
                                          PagoRepositoryPort pagos, ComprobanteRepositoryPort comprobantes,
                                          PagoPort pasarela, EmitirComprobanteUseCase emitirComprobante, Clock reloj) {
        return new PagarPedidoService(envios, pedidos, liquidaciones, compradores, pagos, comprobantes, pasarela,
                emitirComprobante, reloj);
    }

    @Bean
    ConsultarEstadoPagoUseCase consultarEstadoPagoUseCase(EnvioRepositoryPort envios,
                                                         LiquidacionRepositoryPort liquidaciones,
                                                         PagoRepositoryPort pagos,
                                                         ComprobanteRepositoryPort comprobantes) {
        return new ConsultarEstadoPagoService(envios, liquidaciones, pagos, comprobantes);
    }

    @Bean
    ConfirmarPagoUseCase confirmarPagoUseCase(PagoPort pasarela, PagoRepositoryPort pagos,
                                              EmitirComprobanteUseCase emitirComprobante,
                                              EjecutorPorNegocioPort ejecutor, Clock reloj) {
        return new ConfirmarPagoService(pasarela, pagos, emitirComprobante, ejecutor, reloj);
    }

    @Bean
    ConsultarPagosUseCase consultarPagosUseCase(PagoRepositoryPort pagos, ComprobanteRepositoryPort comprobantes,
                                                LiquidacionRepositoryPort liquidaciones) {
        return new ConsultarPagosService(pagos, comprobantes, liquidaciones);
    }

    @Bean
    IniciarSesionUseCase iniciarSesionUseCase(UsuarioRepositoryPort usuarios, NegocioRepositoryPort negocios,
                                              HashClavePort hashes, EmisorTokenPort emisor, Clock reloj) {
        return new IniciarSesionService(usuarios, negocios, hashes, emisor, reloj);
    }
}
