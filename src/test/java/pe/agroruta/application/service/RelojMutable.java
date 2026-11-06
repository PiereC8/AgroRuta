package pe.agroruta.application.service;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

final class RelojMutable extends Clock {

    private Instant ahora;

    RelojMutable(Instant ahora) {
        this.ahora = ahora;
    }

    void avanzar(Duration duracion) {
        ahora = ahora.plus(duracion);
    }

    @Override
    public ZoneId getZone() {
        return ZoneOffset.UTC;
    }

    @Override
    public Clock withZone(ZoneId zona) {
        return this;
    }

    @Override
    public Instant instant() {
        return ahora;
    }
}
