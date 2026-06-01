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


    private static final DescribedPredicate<JavaClass> HIGHSCORE_RUN_INTERNALS =
            new DescribedPredicate<>("highscore run internals") {
                private final java.util.Set<String> helperNames = java.util.Set.of(
                        "HighscorePageFetcher",
                        "HighscoreScopePlanner",
                        "HighscoreCharacterResolver",
                        "HighscoreScopeStateService",
                        "HighscoreFetchRetryPolicy",
                        "HighscoreStatStorageService",
                        "HighscoreScopeWorker",
                        "HighscoreScopeScraper"
                );

                @Override
                public boolean test(JavaClass javaClass) {
                    return helperNames.contains(javaClass.getSimpleName());
                }
            };


    private static final DescribedPredicate<JavaClass> GUILD_DETAIL_ROW_DOM_TYPES =
            new DescribedPredicate<>("guild detail row DOM types") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getName().equals("org.jsoup.nodes.Element")
                            || javaClass.getPackageName().startsWith("org.jsoup.select");
                }
            };


    private static final DescribedPredicate<JavaClass> CHARACTER_DETAILS_PROFILE_DOM_TYPES =
            new DescribedPredicate<>("character details profile DOM types") {
                @Override
                public boolean test(JavaClass javaClass) {
                    return javaClass.getName().equals("org.jsoup.nodes.Element")
                            || javaClass.getPackageName().startsWith("org.jsoup.select");
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
    static final ArchRule highscore_service_should_remain_a_facade_without_direct_run_internals = noClasses()
            .that().haveSimpleName("HighscoreService")
            .should().dependOnClassesThat(HIGHSCORE_RUN_INTERNALS);

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
    static final ArchRule guild_detail_page_parser_should_remain_a_facade_without_row_dom_details = noClasses()
            .that().haveSimpleName("GuildDetailPageParser")
            .should().dependOnClassesThat(GUILD_DETAIL_ROW_DOM_TYPES);


    @ArchTest
    static final ArchRule character_details_page_parser_should_remain_a_facade_without_profile_dom_details = noClasses()
            .that().haveSimpleName("CharacterDetailsPageParser")
            .should().dependOnClassesThat(CHARACTER_DETAILS_PROFILE_DOM_TYPES);

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


    @ArchTest
    static final ArchRule jsoup_highscore_adapter_should_remain_transport_only_without_dom_parsing_details = noClasses()
            .that().haveSimpleName("JsoupHighscoreAdapter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.jsoup.nodes..",
                    "org.jsoup.select.."
            );


    @ArchTest
    static final ArchRule application_should_access_guild_persistence_through_domain_ports = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.nathan.tibiastats.infrastructure.persistence.SpringGuildRepository"
            );


    @ArchTest
    static final ArchRule application_should_access_highscore_scrape_state_through_domain_ports = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.nathan.tibiastats.infrastructure.persistence.HighscoreScrapeStateRepository"
            );


    @ArchTest
    static final ArchRule application_should_access_highscore_stat_records_through_domain_ports = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.nathan.tibiastats.infrastructure.persistence.HighscoreStatRecordWriter"
            );


    @ArchTest
    static final ArchRule auth_flows_should_access_user_accounts_through_domain_ports = noClasses()
            .that().resideInAnyPackage(
                    "..application..",
                    "..config..",
                    "..infrastructure.adapter.web.rest.."
            )
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.nathan.tibiastats.infrastructure.persistence.UserAccountRepository"
            );


    @ArchTest
    static final ArchRule auth_flows_should_access_refresh_tokens_through_domain_ports = noClasses()
            .that().resideInAnyPackage(
                    "..application..",
                    "..config..",
                    "..infrastructure.adapter.web.rest.."
            )
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.nathan.tibiastats.infrastructure.persistence.RefreshTokenRepository"
            );


    @ArchTest
    static final ArchRule auth_flows_should_access_blacklisted_tokens_through_domain_ports = noClasses()
            .that().resideInAnyPackage(
                    "..application..",
                    "..config..",
                    "..infrastructure.adapter.web.rest.."
            )
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.nathan.tibiastats.infrastructure.persistence.BlacklistedTokenRepository"
            );


    @ArchTest
    static final ArchRule application_should_access_scrape_job_execution_through_domain_ports = noClasses()
            .that().resideInAPackage("..application..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.nathan.tibiastats.infrastructure.persistence.ScrapeJobExecutionRepository"
            );


    @ArchTest
    static final ArchRule character_details_service_should_remain_a_facade_without_direct_selection_fetch_or_persistence_dependencies = noClasses()
            .that().haveSimpleName("CharacterDetailsService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..domain.port..",
                    "..config..",
                    "org.springframework.transaction.."
            );


    @ArchTest
    static final ArchRule admin_scraper_service_should_remain_a_facade_without_direct_config_persistence_or_query_access = noClasses()
            .that().haveSimpleName("AdminScraperService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..config..",
                    "..infrastructure.persistence..",
                    "..application.query.."
            );


    @ArchTest
    static final ArchRule core_application_layers_should_not_depend_directly_on_persistence_adapters = noClasses()
            .that().resideInAnyPackage(
                    "..application..",
                    "..config..",
                    "..infrastructure.adapter.web.rest.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure.persistence.."
            );


    @ArchTest
    static final ArchRule character_online_read_model_service_should_remain_a_facade_without_direct_jdbc = noClasses()
            .that().haveSimpleName("CharacterOnlineReadModelService")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..",
                    "java.sql.."
            );


    @ArchTest
    static final ArchRule highscore_scrape_properties_should_delegate_plan_setting_details = noClasses()
            .that().haveSimpleName("HighscoreScrapeProperties")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "com.nathan.tibiastats.domain.model.StatCategory"
            );


    @ArchTest
    static final ArchRule highscore_stat_record_writer_should_remain_a_facade_without_direct_jdbc_or_sql_details = noClasses()
            .that().haveSimpleName("HighscoreStatRecordWriter")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "org.springframework.jdbc..",
                    "java.sql.."
            );


    @ArchTest
    static final ArchRule application_and_configuration_layers_should_not_depend_on_scraper_implementation_details = noClasses()
            .that().resideInAnyPackage(
                    "..application..",
                    "..config.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure.adapter.scraper..",
                    "org.jsoup.."
            );


    @ArchTest
    static final ArchRule web_adapters_should_not_depend_on_scraper_or_jdbc_implementation_details = noClasses()
            .that().resideInAnyPackage(
                    "..infrastructure.adapter.web.."
            )
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..infrastructure.adapter.scraper..",
                    "org.jsoup..",
                    "org.springframework.jdbc..",
                    "java.sql.."
            );


    @ArchTest
    static final ArchRule scraper_adapters_should_not_depend_on_application_or_persistence_layers = noClasses()
            .that().resideInAPackage("..infrastructure.adapter.scraper..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "..application..",
                    "..infrastructure.persistence.."
            );

}
