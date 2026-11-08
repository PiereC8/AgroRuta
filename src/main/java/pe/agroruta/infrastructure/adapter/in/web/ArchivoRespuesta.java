package pe.agroruta.infrastructure.adapter.in.web;

import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import pe.agroruta.domain.model.Archivo;

/** Respuesta de descarga de un archivo generado, sin guardarlo en caché. */
final class ArchivoRespuesta {

    private ArchivoRespuesta() {
    }

    static ResponseEntity<byte[]> descarga(Archivo archivo) {
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment().filename(archivo.nombre()).build().toString())
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .contentType(MediaType.parseMediaType(archivo.tipoContenido()))
                .body(archivo.contenido());
    }
}
