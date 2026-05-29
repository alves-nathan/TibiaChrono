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
    static final ArchRule api_query_service_should_remain_a_facade_without_direct_jdbc = noClasses()
            .that().haveSimpleName("ApiQueryService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..",
                    "java.sql.."
            );

    @ArchTest
    static final ArchRule write_side_application_services_should_not_use_spring_jdbc_directly = noClasses()
            .that().resideInAPackage("..application.service..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc.."
            );


    @ArchTest
    static final ArchRule highscore_service_should_remain_an_orchestrator_without_direct_domain_ports = noClasses()
            .that().haveSimpleName("HighscoreService")
            .should().dependOnClassesThat().resideInAnyPackage("..domain.port..");


    @ArchTest
    static final ArchRule highscore_api_query_service_should_remain_a_facade_without_direct_jdbc = noClasses()
            .that().haveSimpleName("HighscoreApiQueryService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..",
                    "java.sql.."
            );


    @ArchTest
    static final ArchRule character_timeline_service_should_remain_a_facade_without_direct_jdbc = noClasses()
            .that().haveSimpleName("CharacterTimelineService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..",
                    "java.sql.."
            );


    @ArchTest
    static final ArchRule world_online_analytics_service_should_remain_a_facade_without_direct_jdbc = noClasses()
            .that().haveSimpleName("WorldOnlineAnalyticsService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..",
                    "java.sql.."
            );


    @ArchTest
    static final ArchRule guild_scrape_service_should_remain_an_orchestrator_without_direct_repositories_or_ports = noClasses()
            .that().haveSimpleName("GuildScrapeService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..domain.port..",
                    "..infrastructure.persistence.."
            );


    @ArchTest
    static final ArchRule scrape_service_should_remain_an_orchestrator_without_direct_repositories_or_ports = noClasses()
            .that().haveSimpleName("ScrapeService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..domain.port..",
                    "..infrastructure.persistence.."
            );


    @ArchTest
    static final ArchRule character_naming_service_should_remain_a_facade_without_direct_repositories_or_ports = noClasses()
            .that().haveSimpleName("CharacterNamingService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..domain.port..",
                    "..infrastructure.persistence.."
            );


    @ArchTest
    static final ArchRule guild_query_service_should_remain_a_facade_without_direct_repositories_or_ports = noClasses()
            .that().haveSimpleName("GuildQueryService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..domain.port..",
                    "..infrastructure.persistence.."
            );


    @ArchTest
    static final ArchRule jsoup_guild_adapter_should_remain_transport_only_without_dom_parsing_details = noClasses()
            .that().haveSimpleName("JsoupGuildAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.jsoup.nodes..",
                    "org.jsoup.select.."
            );

    @ArchTest
    static final ArchRule jsoup_world_adapter_should_remain_transport_only_without_dom_parsing_details = noClasses()
            .that().haveSimpleName("JsoupScrapeAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.jsoup.nodes..",
                    "org.jsoup.select.."
            );


    @ArchTest
    static final ArchRule jsoup_character_adapter_should_remain_transport_only_without_dom_parsing_details = noClasses()
            .that().haveSimpleName("JsoupCharacterAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.jsoup.nodes..",
                    "org.jsoup.select.."
            );

}
