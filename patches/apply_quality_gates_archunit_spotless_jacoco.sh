#!/usr/bin/env bash
set -Eeuo pipefail

BACKUP_SUFFIX=".bak-quality-gates-$(date +%Y%m%d%H%M%S)"

if [[ ! -f "pom.xml" || ! -d "src/main/java" ]]; then
  echo "ERROR: run this script from the project root." >&2
  exit 1
fi

cp "pom.xml" "pom.xml$BACKUP_SUFFIX"
cp "Makefile" "Makefile$BACKUP_SUFFIX"

python3 - <<'PY'
from pathlib import Path

pom = Path('pom.xml')
text = pom.read_text()
original = text

if '<archunit.version>' not in text:
    text = text.replace(
        '        <jacoco.version>0.8.12</jacoco.version>\n',
        '        <jacoco.version>0.8.12</jacoco.version>\n'
        '        <archunit.version>1.3.0</archunit.version>\n'
        '        <maven-enforcer-plugin.version>3.5.0</maven-enforcer-plugin.version>\n'
        '        <spotless-maven-plugin.version>2.43.0</spotless-maven-plugin.version>\n'
        '        <jacoco.minimum.coverage>0.15</jacoco.minimum.coverage>\n'
    )

archunit_dep = '''
        <!-- Architecture fitness functions -->
        <dependency>
            <groupId>com.tngtech.archunit</groupId>
            <artifactId>archunit-junit5</artifactId>
            <version>${archunit.version}</version>
            <scope>test</scope>
        </dependency>
'''
if '<artifactId>archunit-junit5</artifactId>' not in text:
    text = text.replace('        <!-- JSONPath helper used in tests -->\n', archunit_dep + '\n        <!-- JSONPath helper used in tests -->\n')

enforcer_plugin = '''
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-enforcer-plugin</artifactId>
                <version>${maven-enforcer-plugin.version}</version>
                <executions>
                    <execution>
                        <id>enforce-java-and-maven</id>
                        <goals>
                            <goal>enforce</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <requireJavaVersion>
                                    <version>[21,)</version>
                                </requireJavaVersion>
                                <requireMavenVersion>
                                    <version>[3.9.0,)</version>
                                </requireMavenVersion>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>

            <plugin>
                <groupId>com.diffplug.spotless</groupId>
                <artifactId>spotless-maven-plugin</artifactId>
                <version>${spotless-maven-plugin.version}</version>
                <configuration>
                    <java>
                        <removeUnusedImports />
                        <formatAnnotations />
                        <googleJavaFormat>
                            <version>1.25.2</version>
                            <style>AOSP</style>
                        </googleJavaFormat>
                    </java>
                </configuration>
            </plugin>
'''
if '<artifactId>maven-enforcer-plugin</artifactId>' not in text:
    text = text.replace('            <plugin>\n                <groupId>org.jacoco</groupId>', enforcer_plugin + '\n            <plugin>\n                <groupId>org.jacoco</groupId>')

old_jacoco = '''            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>${jacoco.version}</version>
            </plugin>'''
new_jacoco = '''            <plugin>
                <groupId>org.jacoco</groupId>
                <artifactId>jacoco-maven-plugin</artifactId>
                <version>${jacoco.version}</version>
                <executions>
                    <execution>
                        <id>prepare-agent</id>
                        <goals>
                            <goal>prepare-agent</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>report</id>
                        <phase>verify</phase>
                        <goals>
                            <goal>report</goal>
                        </goals>
                    </execution>
                    <execution>
                        <id>check</id>
                        <phase>verify</phase>
                        <goals>
                            <goal>check</goal>
                        </goals>
                        <configuration>
                            <rules>
                                <rule>
                                    <element>BUNDLE</element>
                                    <limits>
                                        <limit>
                                            <counter>LINE</counter>
                                            <value>COVEREDRATIO</value>
                                            <minimum>${jacoco.minimum.coverage}</minimum>
                                        </limit>
                                    </limits>
                                </rule>
                            </rules>
                        </configuration>
                    </execution>
                </executions>
            </plugin>'''
if '<id>prepare-agent</id>' not in text:
    text = text.replace(old_jacoco, new_jacoco)

if text != original:
    pom.write_text(text)

# ArchUnit architecture tests: intentionally starts with rules that fit the current codebase.
arch_dir = Path('src/test/java/com/nathan/tibiastats/architecture')
arch_dir.mkdir(parents=True, exist_ok=True)
arch_test = arch_dir / 'ArchitectureRulesTest.java'
arch_test.write_text('''package com.nathan.tibiastats.architecture;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;
import org.springframework.web.bind.annotation.RestController;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@AnalyzeClasses(packages = "com.nathan.tibiastats", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureRulesTest {

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
''')

makefile = Path('Makefile')
mk = makefile.read_text()
original_mk = mk
help_insert = '\t@echo "  make qa            - run full quality gate: tests, ArchUnit and JaCoCo check"\n\t@echo "  make arch-test     - run architecture fitness tests only"\n\t@echo "  make format        - apply Java formatter via Spotless"\n\t@echo "  make format-check  - verify Java formatting via Spotless"'
if 'make qa' not in mk:
    marker = '\t@echo "  make env-print     - show important env vars"'
    mk = mk.replace(marker, marker + '\n' + help_insert)

quality_block = r'''

# ---- Quality gates ----
.PHONY: qa
qa:
	MAVEN_ARGS="-U clean verify" ./run-tests.sh

.PHONY: arch-test
arch-test:
	MAVEN_ARGS="-U -Dtest=ArchitectureRulesTest test" ./run-tests.sh

.PHONY: format
format:
	MAVEN_ARGS="-U spotless:apply" ./run-tests.sh

.PHONY: format-check
format-check:
	MAVEN_ARGS="-U spotless:check" ./run-tests.sh
'''
if '.PHONY: qa' not in mk:
    mk += quality_block

if mk != original_mk:
    makefile.write_text(mk)
PY

cat <<'MSG'

Done.
Quality gates added.

New commands:
  make qa
  make arch-test
  make format
  make format-check

Notes:
  - JaCoCo starts with a conservative 15% line coverage gate to avoid blocking legacy cleanup immediately.
  - Raise jacoco.minimum.coverage gradually as the codebase gains characterization tests.
MSG
