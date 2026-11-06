package pe.agroruta;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

@AnalyzeClasses(packages = "pe.agroruta", importOptions = ImportOption.DoNotIncludeTests.class)
class ArquitecturaTest {

    @ArchTest
    static final ArchRule dominio_no_depende_de_frameworks = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework..",
                    "jakarta.persistence..",
                    "jakarta.servlet..",
                    "com.fasterxml.jackson..");

    @ArchTest
    static final ArchRule dominio_no_depende_de_otras_capas = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage("..application..", "..infrastructure..");

    @ArchTest
    static final ArchRule aplicacion_no_depende_de_infraestructura = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().resideInAPackage("..infrastructure..");
}
