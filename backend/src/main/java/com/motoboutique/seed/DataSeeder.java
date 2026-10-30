package com.motoboutique.seed;

import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Ejecuta, al arrancar la aplicación, todas las fases de datos iniciales en su orden.
 * Solo se ejecuta cuando app.seed.enabled=true.
 */
@Slf4j
@Component
public class DataSeeder implements ApplicationRunner {

    private final List<SeederFase> fases;
    private final boolean habilitado;

    public DataSeeder(List<SeederFase> fases, @Value("${app.seed.enabled:false}") boolean habilitado) {
        this.fases = fases;
        this.habilitado = habilitado;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!habilitado) {
            log.info("DataSeeder desactivado (app.seed.enabled=false)");
            return;
        }
        log.info("DataSeeder: ejecutando {} fase(s) de datos iniciales", fases.size());
        for (SeederFase fase : fases) {
            log.info("DataSeeder: {}", fase.getClass().getSimpleName());
            fase.ejecutar();
        }
    }
}
