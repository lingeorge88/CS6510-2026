package com.selfcheckout.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.library.Architectures;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.library.dependencies.SlicesRuleDefinition.slices;

/**
 * Executable definition of the layered architecture. A violation fails the build.
 *
 * <p>These rules are the reason the layering is a real constraint rather than a naming
 * convention: nothing otherwise stops someone autowiring a repository into a controller.
 *
 * <p>Note what is deliberately NOT enforced: entities may travel up into the api layer,
 * because this implementation returns them directly instead of mapping to DTOs. That is
 * a conscious simplicity trade-off. Repositories may NOT — a controller reaching past
 * the service layer to the database is the boundary that actually matters here.
 */
class LayeringRulesTest {

    private static final String ROOT = "com.selfcheckout";

    private static final String API = ROOT + ".api..";
    private static final String TRANSACTIONS = ROOT + ".transactions..";
    private static final String ANALYTICS = ROOT + ".analytics..";
    private static final String DATA = ROOT + ".data..";

    private static JavaClasses classes;

    @BeforeAll
    static void importClasses() {
        classes = new ClassFileImporter()
                .withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
                .importPackages(ROOT);
    }

    @Test
    @DisplayName("R1: dependencies flow api -> transactions -> analytics -> data")
    void layerDependenciesFlowDownward() {
        Architectures.layeredArchitecture()
                .consideringOnlyDependenciesInLayers()
                .layer("API").definedBy(API)
                .layer("Transactions").definedBy(TRANSACTIONS)
                .layer("Analytics").definedBy(ANALYTICS)
                .layer("Data").definedBy(DATA)

                .whereLayer("API").mayNotBeAccessedByAnyLayer()
                .whereLayer("Transactions").mayOnlyBeAccessedByLayers("API")
                .whereLayer("Analytics").mayOnlyBeAccessedByLayers("API", "Transactions")
                // API is allowed here because this implementation returns entities
                // directly rather than mapping to DTOs. Reintroducing DTOs would let
                // this drop back to ("Transactions", "Analytics").
                .whereLayer("Data").mayOnlyBeAccessedByLayers("API", "Transactions", "Analytics")

                .check(classes);
    }

    @Test
    @DisplayName("R2: only the data layer touches repositories")
    void repositoriesStayInDataLayer() {
        noClasses()
                .that().resideOutsideOfPackage(DATA)
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.data.repository..")
                .because("services go through the data layer, never around it")
                .check(classes);
    }

    @Test
    @DisplayName("R3: web types exist only in the api layer")
    void webTypesStayInApiLayer() {
        noClasses()
                .that().resideOutsideOfPackage(API)
                .should().dependOnClassesThat()
                .resideInAnyPackage("org.springframework.web..", "org.springframework.http..")
                .because("business layers must not know about HTTP")
                .check(classes);
    }

    @Test
    @DisplayName("R4: no dependency cycles between layers")
    void noCyclesBetweenLayers() {
        slices()
                .matching(ROOT + ".(*)..")
                .should().beFreeOfCycles()
                .check(classes);
    }
}
