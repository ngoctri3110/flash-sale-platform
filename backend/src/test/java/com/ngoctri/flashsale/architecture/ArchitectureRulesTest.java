package com.ngoctri.flashsale.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import org.junit.jupiter.api.Test;

class ArchitectureRulesTest {

    private static final JavaClasses APPLICATION_CLASSES =
            new ClassFileImporter().importPackages("com.ngoctri.flashsale");

    @Test
    void domainMustRemainIndependentOfFrameworksAndOuterLayers() {
        noClasses()
                .that()
                .resideInAPackage("..domain..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(
                        "org.springframework..",
                        "jakarta.persistence..",
                        "..application..",
                        "..api..",
                        "..infrastructure..")
                .allowEmptyShould(true)
                .check(APPLICATION_CLASSES);
    }

    @Test
    void applicationMustNotDependOnDeliveryOrInfrastructure() {
        noClasses()
                .that()
                .resideInAPackage("..application..")
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage("..api..", "..infrastructure..")
                .allowEmptyShould(true)
                .check(APPLICATION_CLASSES);
    }

    @Test
    void deliveryMustNotReachIntoInfrastructure() {
        noClasses()
                .that()
                .resideInAPackage("..api..")
                .should()
                .dependOnClassesThat()
                .resideInAPackage("..infrastructure..")
                .allowEmptyShould(true)
                .check(APPLICATION_CLASSES);
    }
}
