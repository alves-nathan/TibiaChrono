package com.nathan.tibiastats.architecture;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.nathan.tibiastats", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

    
    private static final DescribedPredicate<JavaClass> TOP_LEVEL_CLASS =
            new DescribedPredicate<>("top-level classes") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return !javaClass.getName().contains("$");
                }
            };

@ArchTest
    static final ArchRule domain_should_not_depend_on_application_infrastructure_or_config = noClasses()
            .that().resideInAPackage("..domain..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..",
                    "..infrastructure..",
                    "..config.."
            );

    @ArchTest
    static final ArchRule domain_ports_should_remain_interfaces = classes()
            .that().resideInAPackage("..domain.port..")
                .and(TOP_LEVEL_CLASS)
            .should().beInterfaces();

    @ArchTest
    static final ArchRule rest_web_adapters_should_be_named_controllers = classes()
            .that().resideInAPackage("..infrastructure.adapter.web.rest..")
            .and().areAnnotatedWith(RestController.class)
            .should().haveSimpleNameEndingWith("Controller");

    @ArchTest
    static final ArchRule domain_ports_should_not_depend_on_infrastructure = noClasses()
            .that().resideInAPackage("..domain.port..")
            .should().dependOnClassesThat().resideInAnyPackage("..infrastructure..");
}
