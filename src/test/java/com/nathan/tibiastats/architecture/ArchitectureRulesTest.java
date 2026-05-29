package com.nathan.tibiastats.architecture;

import com.nathan.tibiastats.application.query.ReadModelComponent;
import com.nathan.tibiastats.application.query.ReadModelService;
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

    @ArchTest
    static final ArchRule read_model_services_should_live_in_query_package = classes()
            .that().areAnnotatedWith(ReadModelService.class)
            .should().resideInAPackage("..application.query..");

    @ArchTest
    static final ArchRule write_application_services_should_not_depend_on_spring_jdbc = noClasses()
            .that().resideInAPackage("..application.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..",
                    "org.springframework.jdbc.core..",
                    "org.springframework.jdbc.core.namedparam.."
            );


    private static final DescribedPredicate<JavaClass> NOT_READ_MODEL_MARKER =
            new DescribedPredicate<>("not the ReadModelService marker") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return !javaClass.getSimpleName().equals("ReadModelService");
                }
            };

    @ArchTest
    static final ArchRule query_services_should_be_marked_as_read_models = classes()
            .that().resideInAPackage("..application.query..")
            .and().haveSimpleNameEndingWith("Service")
            .and(NOT_READ_MODEL_MARKER)
            .should().beAnnotatedWith(ReadModelComponent.class);

    @ArchTest
    static final ArchRule write_side_application_services_should_not_use_spring_jdbc_directly = noClasses()
            .that().resideInAPackage("..application.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc.."
            );

}
