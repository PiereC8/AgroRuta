package pe.agroruta;

import java.util.TimeZone;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

@SpringBootApplication
public class AgroRutaApplication {

    public static void main(String[] args) {
        TimeZone.setDefault(TimeZone.getTimeZone("America/Lima"));
        SpringApplication.run(AgroRutaApplication.class, args);
    }
}
