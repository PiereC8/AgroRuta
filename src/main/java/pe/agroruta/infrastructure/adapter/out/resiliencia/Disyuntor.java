package pe.agroruta.infrastructure.adapter.out.resiliencia;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.function.Supplier;

import pe.agroruta.domain.model.ServicioEnPausaException;
import pe.agroruta.domain.model.ServicioExternoNoDisponibleException;

/**
 * Circuit Breaker: tras varias caídas seguidas deja de llamar al servicio durante una pausa, y luego deja pasar una
 * sola llamada de prueba. Solo cuentan como caída las excepciones de servicio no disponible: un rechazo del negocio
 * (tarjeta sin fondos, por ejemplo) es una respuesta normal.
 */
public class Disyuntor {

    private enum Estado { CERRADO, ABIERTO, PROBANDO }

    private final String nombre;
    private final int fallosParaAbrir;
    private final Duration pausa;
    private final Clock reloj;

    private Estado estado = Estado.CERRADO;
    private int fallosSeguidos;
    private Instant abiertoHasta;

    public Disyuntor(String nombre, int fallosParaAbrir, Duration pausa, Clock reloj) {
        this.nombre = nombre;
        this.fallosParaAbrir = fallosParaAbrir;
        this.pausa = pausa;
        this.reloj = reloj;
    }

    public <T> T ejecutar(Supplier<T> llamada) {
        boolean esPrueba = reservarPaso();
        try {
            T resultado = llamada.get();
            registrarExito();
            return resultado;
        } catch (ServicioExternoNoDisponibleException e) {
            registrarCaida(esPrueba);
            throw e;
        } catch (RuntimeException e) {
            liberarPrueba(esPrueba);
            throw e;
        }
    }

    /** Decide si la llamada puede pasar. Devuelve verdadero si es la llamada de prueba tras una pausa. */
    private synchronized boolean reservarPaso() {
        if (estado == Estado.ABIERTO) {
            if (reloj.instant().isBefore(abiertoHasta)) {
                throw new ServicioEnPausaException(nombre + " está en pausa por fallos seguidos");
            }
            estado = Estado.PROBANDO;
            return true;
        }
        if (estado == Estado.PROBANDO) {
            throw new ServicioEnPausaException(nombre + " está en pausa mientras se hace una prueba");
        }
        return false;
    }

    private synchronized void registrarExito() {
        estado = Estado.CERRADO;
        fallosSeguidos = 0;
    }

    private synchronized void registrarCaida(boolean esPrueba) {
        fallosSeguidos++;
        if (esPrueba || fallosSeguidos >= fallosParaAbrir) {
            estado = Estado.ABIERTO;
            abiertoHasta = reloj.instant().plus(pausa);
        }
    }

    /** Un error que no es de servicio durante la prueba no dice nada del servicio: se permite probar otra vez. */
    private synchronized void liberarPrueba(boolean esPrueba) {
        if (esPrueba && estado == Estado.PROBANDO) {
            estado = Estado.ABIERTO;
            abiertoHasta = reloj.instant();
        }
    }
}
