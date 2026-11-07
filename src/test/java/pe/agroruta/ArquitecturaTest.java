package pe.agroruta;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/** Funciones de aptitud: si alguien rompe la arquitectura, la compilación en GitHub falla. */
@AnalyzeClasses(packages = "pe.agroruta", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    // ---------- Regla de dependencia ----------

    @ArchTest
    static final ArchRule dominio_no_depende_de_frameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..",
                    "jakarta.mail..",
                    "com.lowagie..",
                    "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule aplicacion_no_depende_de_el_correo_ni_del_pdf = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAnyPackage("jakarta.mail..", "com.lowagie..",
                    "org.springframework.mail..");

    @ArchTest
    static final ArchRule los_exportadores_y_el_correo_implementan_un_puerto_de_salida = classes()
            .that().resideInAnyPackage("..infrastructure.adapter.out.reporte..",
                    "..infrastructure.adapter.out.correo..")
            .and().areTopLevelClasses()
            .should().implement(JavaClass.Predicates.resideInAPackage("..domain.port.out.."));

    @ArchTest
    static final ArchRule dominio_no_depende_de_otras_capas = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule aplicacion_no_depende_de_infraestructura = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");

    @ArchTest
    static final ArchRule los_controladores_no_tocan_la_base_de_datos = noClasses()
            .that().resideInAPackage("..infrastructure.adapter.in..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure.adapter.out.persistence..");

    @ArchTest
    static final ArchRule no_hay_ciclos_entre_las_capas = slices()
            .matching("pe.agroruta.(*)..")
            .should().beFreeOfCycles();

    @ArchTest
    static final ArchRule no_hay_ciclos_entre_el_modelo_y_los_puertos = slices()
            .matching("pe.agroruta.domain.(*)..")
            .should().beFreeOfCycles();

    // ---------- Modelo del dominio ----------

    /**
     * Los datos del dominio son inmutables: registros, enumeraciones, excepciones o clases de reglas que no se
     * instancian (constructor privado).
     */
    @ArchTest
    static final ArchRule el_modelo_del_dominio_es_inmutable = classes()
            .that().resideInAPackage("..domain.model..")
            .and().areTopLevelClasses()
            .and().areNotEnums()
            .and().areNotInterfaces()
            .and().haveSimpleNameNotEndingWith("Exception")
            .and().areNotRecords()
            .should().haveOnlyPrivateConstructors();

    // ---------- Nombres que cuentan la arquitectura ----------

    @ArchTest
    static final ArchRule los_puertos_de_entrada_terminan_en_UseCase = classes()
            .that().resideInAPackage("..domain.port.in..")
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("UseCase").andShould().beInterfaces();

    @ArchTest
    static final ArchRule los_puertos_de_salida_terminan_en_Port = classes()
            .that().resideInAPackage("..domain.port.out..")
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Port").andShould().beInterfaces();

    @ArchTest
    static final ArchRule los_servicios_de_aplicacion_implementan_un_caso_de_uso = classes()
            .that().resideInAPackage("..application.service..")
            .and().areTopLevelClasses()
            .should().haveSimpleNameEndingWith("Service")
            .andShould().implement(JavaClass.Predicates.resideInAPackage("..domain.port.in.."));

    @ArchTest
    static final ArchRule los_adaptadores_de_salida_implementan_un_puerto = classes()
            .that().resideInAPackage("..infrastructure.adapter.out..")
            .and().haveSimpleNameEndingWith("Adapter")
            .should().implement(JavaClass.Predicates.resideInAPackage("..domain.port.out.."));
}
