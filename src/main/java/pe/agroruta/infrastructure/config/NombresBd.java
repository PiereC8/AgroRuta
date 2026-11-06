package pe.agroruta.infrastructure.config;

import java.net.URI;

/** Arma el nombre de la base de cada negocio y su URL de conexión, a partir de la URL de la base central. */
public record NombresBd(String urlPlataforma, String prefijo, String sufijo) {

    public String deIdentificador(String identificadorNegocio) {
        return prefijo + identificadorNegocio + sufijo;
    }

    public String urlDeIdentificador(String identificadorNegocio) {
        return urlConBase(urlPlataforma, deIdentificador(identificadorNegocio));
    }

    static String urlConBase(String url, String base) {
        URI uri = URI.create(url.substring("jdbc:".length()));
        String puerto = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        String consulta = uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery();
        return "jdbc:" + uri.getScheme() + "://" + uri.getHost() + puerto + "/" + base + consulta;
    }

    static String creandoBaseSiFalta(String url) {
        if (url.contains("createDatabaseIfNotExist")) {
            return url;
        }
        return url + (url.contains("?") ? "&" : "?") + "createDatabaseIfNotExist=true";
    }
}
