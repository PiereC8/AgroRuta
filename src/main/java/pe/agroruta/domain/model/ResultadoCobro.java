package pe.agroruta.domain.model;

import java.util.Map;

public record ResultadoCobro(Estado estado, String idCargo, String codigoRespuesta, String mensajePasarela,
                             String marcaTarjeta, String ultimosCuatro, Map<String, String> metadatos) {

    public enum Estado {
        APROBADO,
        RECHAZADO,
        PENDIENTE
    }

    public static ResultadoCobro aprobado(String idCargo, String marcaTarjeta, String ultimosCuatro,
                                          Map<String, String> metadatos) {
        return new ResultadoCobro(Estado.APROBADO, idCargo, "venta_exitosa", null, marcaTarjeta, ultimosCuatro,
                metadatos);
    }

    public static ResultadoCobro rechazado(String idCargo, String codigoRespuesta, String mensajePasarela,
                                           Map<String, String> metadatos) {
        return new ResultadoCobro(Estado.RECHAZADO, idCargo, codigoRespuesta, mensajePasarela, null, null,
                metadatos);
    }
}
