/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.dap.gradle;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import java.util.function.Predicate;

import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.support.PsiPropertyValueElement;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiRecursiveElementVisitor;
import com.intellij.testFramework.LightProjectDescriptor;
import com.intellij.testFramework.fixtures.CodeInsightTestFixture;
import com.intellij.testFramework.fixtures.IdeaProjectTestFixture;
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory;
import com.intellij.testFramework.fixtures.TestFixtureBuilder;
import com.intellij.testFramework.junit5.RunInEdt;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level integration tests for the version-element resolution.
 *
 * @author Mark Paluch
 */
@RunInEdt(writeIntent = true)
class VersionUpgradeLookupServiceTests {

	/**
	 * The Groovy build script shared by all tests in this class.
	 */
	private static final String BUILD_GRADLE = """
			plugins {
			    id 'groovy'
			    id 'org.springframework.boot' version '4.0.3'
			    id 'io.spring.dependency-management' version '1.1.7'
			}

			ext {
			    set('springModulithVersion', "2.0.4")
			}

			dependencies {
			    implementation 'org.apache.groovy:groovy'
			    implementation 'org.junit:junit-bom:6.0.0'
			}

			dependencyManagement {
			    imports {
			        mavenBom "org.springframework.modulith:spring-modulith-bom:${springModulithVersion}"
			    }
			}
			""";

	private CodeInsightTestFixture fixture;

	@BeforeEach
	void setUp() throws Exception {
		TestFixtureBuilder<IdeaProjectTestFixture> builder = IdeaTestFixtureFactory.getFixtureFactory()
				.createLightFixtureBuilder(new LightProjectDescriptor(), getClass().getSimpleName());
		fixture = IdeaTestFixtureFactory.getFixtureFactory().createCodeInsightFixture(builder.getFixture());
		fixture.setUp();
	}

	@AfterEach
	void tearDown() throws Exception {
		fixture.tearDown();
		fixture = null;
	}

	@Test
	void junitBomInlineVersionYieldsSingleHit() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		List<DependencyAndVersionLocation> hits = findVersionLocations(file,
				loc -> loc.artifactId().groupId().startsWith("org.junit"));

		assertThat(hits).as("exactly one hit for org.junit:junit-bom:6.0.0").hasSize(1);
		DependencyAndVersionLocation loc = hits.get(0);
		assertThat(loc.artifactId().groupId()).isEqualTo("org.junit");
		assertThat(loc.artifactId().artifactId()).isEqualTo("junit-bom");
		assertThat(loc.isPropertyReference()).isFalse();
	}

	@Test
	void managedBomWithInterpolatedVersionYieldsSingleHit() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		List<DependencyAndVersionLocation> hits = findVersionLocations(file,
				loc -> loc.artifactId().groupId().startsWith("org.springframework.modulith"));

		assertThat(hits).as("exactly one hit for org.springframework.modulith:spring-modulith-bom").hasSize(1);
	}

	@Test
	void inlineVersionLocationHasCorrectCoordinates() {

		PsiFile file = fixture.configureByText("build.gradle", """
					dependencies {
				    implementation 'org.junit:junit-bom:6.0.0'
				}
				""");

		List<DependencyAndVersionLocation> all = findVersionLocations(file, loc -> true);

		assertThat(all).hasSize(1);
	}

	@Test
	void managedBomVersionIsDetectedAsPropertyReference() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		DependencyAndVersionLocation loc = findVersionLocation(file,
				l -> "org.springframework.modulith".equals(l.artifactId().groupId())
						&& "spring-modulith-bom".equals(l.artifactId().artifactId()));

		assertThat(loc).as("VersionLocation for spring-modulith-bom").isNotNull();
		assertThat(loc.isPropertyReference()).isTrue();
	}

	@Test
	void dependencyWithoutVersionYieldsNoHit() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		assertThat(findVersionLocations(file, loc -> loc.artifactId().groupId().startsWith("org.apache.groovy")))
				.as("no hit for versionless dependency").isEmpty();
	}

	@Test
	void pluginIdStringLiteralsYieldNoHit() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		// No VersionLocation should have an underlying element whose text is a bare plugin-ID string
		// (contains the plugin ID but no ":" separator — i.e. not a GAV string).
		assertThat(findVersionLocations(file,
				loc -> loc.version().getText().contains("org.springframework.boot")
						&& !loc.version().getText().contains(":")))
				.as("no hits from plugin id string literals").isEmpty();
	}

	@Test
	void extSetArgumentsYieldNoHitFromFindGroovyVersionElement() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		// Match only the exact key/value literals from ext { set('springModulithVersion', "2.0.4") }.
		// Single-quoted key and double-quoted value are distinct from the BOM string.
		assertThat(findVersionLocations(file, loc -> "'springModulithVersion'".equals(loc.version().getText())
				|| "\"2.0.4\"".equals(loc.version().getText()))).as("no hits from ext set() arguments").isEmpty();
	}

	@Test
	void pluginVersionLiteralsAreDetected() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		List<DependencyAndVersionLocation> springBootHits = findVersionLocations(file,
				loc -> loc.artifactId().groupId().startsWith("org.springframework.boot"));
		assertThat(springBootHits).as("one hit for org.springframework.boot plugin").hasSize(1);
		DependencyAndVersionLocation bootLoc = springBootHits.get(0);
		assertThat(bootLoc.artifactId().groupId()).isEqualTo("org.springframework.boot");
		assertThat(bootLoc.artifactId().artifactId()).isEqualTo("org.springframework.boot");
		assertThat(bootLoc.dependency().getVersionSource()).isEqualTo(VersionSource.declared("4.0.3"));
		assertThat(bootLoc.isPropertyReference()).isFalse();

		List<DependencyAndVersionLocation> depMgmtHits = findVersionLocations(file,
				loc -> loc.artifactId().groupId().startsWith("io.spring.dependency-management"));
		assertThat(depMgmtHits).as("one hit for io.spring.dependency-management plugin").hasSize(1);
		DependencyAndVersionLocation dmLoc = depMgmtHits.get(0);
		assertThat(dmLoc.artifactId().groupId()).isEqualTo("io.spring.dependency-management");
		assertThat(dmLoc.dependency().getVersionSource()).isEqualTo(VersionSource.declared("1.1.7"));
		assertThat(dmLoc.isPropertyReference()).isFalse();
	}

	@Test
	void pluginVersionLiteralsYieldSingleHitEach() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		assertThat(findVersionLocations(file, loc -> loc.artifactId().groupId().startsWith("org.springframework.boot")))
				.as("single hit for org.springframework.boot plugin version").hasSize(1);
		assertThat(
				findVersionLocations(file, loc -> loc.artifactId().groupId().startsWith("io.spring.dependency-management")))
				.as("single hit for io.spring.dependency-management plugin version").hasSize(1);
	}

	@Test
	void pluginWithoutVersionYieldsNoHit() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		assertThat(findVersionLocations(file, loc -> loc.artifactId().groupId().startsWith("groovy")))
				.as("no hit for versionless groovy plugin").isEmpty();
	}

	@Test
	void onlyVersionedDependencyDeclarationsProduceHits() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		List<DependencyAndVersionLocation> allHits = findVersionLocations(file, loc -> true);

		assertThat(allHits).as("exactly four versioned declarations in the whole file").hasSize(4);

		// Plugin: org.springframework.boot version '4.0.3'
		assertThat(allHits).anySatisfy(loc -> {
			assertThat(loc.artifactId().groupId()).isEqualTo("org.springframework.boot");
			assertThat(loc.isPropertyReference()).isFalse();
		});

		// Plugin: io.spring.dependency-management version '1.1.7'
		assertThat(allHits).anySatisfy(loc -> {
			assertThat(loc.artifactId().groupId()).isEqualTo("io.spring.dependency-management");
			assertThat(loc.isPropertyReference()).isFalse();
		});

		// junit-bom with inline version
		assertThat(allHits).anySatisfy(loc -> {
			assertThat(loc.artifactId().groupId()).isEqualTo("org.junit");
			assertThat(loc.artifactId().artifactId()).isEqualTo("junit-bom");
			assertThat(loc.isPropertyReference()).isFalse();
		});

		// spring-modulith-bom with property reference
		assertThat(allHits).anySatisfy(loc -> {
			assertThat(loc.artifactId().groupId()).isEqualTo("org.springframework.modulith");
			assertThat(loc.artifactId().artifactId()).isEqualTo("spring-modulith-bom");
			assertThat(loc.isPropertyReference()).isTrue();
		});
	}

	@Test
	void extSetCallValueIsDetectedAsPropertyVersionElement() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		PsiPropertyValueElement loc = findExtProperty(file, l -> "2.0.4".equals(l.propertyValue()));

		assertThat(loc).as("PropertyVersionLocation for set() value").isNotNull();
		assertThat(loc.propertyKey()).isEqualTo("springModulithVersion");
		assertThat(loc.propertyValue()).isEqualTo("2.0.4");
	}

	@Test
	void extSetCallKeyIsNotDetectedAsPropertyVersionElement() {

		PsiFile file = fixture.configureByText("build.gradle", BUILD_GRADLE);

		PsiPropertyValueElement loc = findExtProperty(file,
				l -> "'springModulithVersion'".equals(l.element().getText()));

		assertThat(loc).as("no PropertyVersionLocation for set() key").isNull();
	}

	@Test
	void extAssignmentInsideExtBlockIsDetected() {

		PsiFile file = fixture.configureByText("build.gradle", """
				ext {
				    springVersion = '3.5.0'
				}
				""");

		PsiPropertyValueElement loc = findExtProperty(file, l -> "3.5.0".equals(l.propertyValue()));

		assertThat(loc).as("PropertyVersionLocation for ext block assignment").isNotNull();
		assertThat(loc.propertyKey()).isEqualTo("springVersion");
		assertThat(loc.propertyValue()).isEqualTo("3.5.0");
	}

	@Test
	void extDotAssignmentIsDetected() {

		PsiFile file = fixture.configureByText("build.gradle", """
				ext.springVersion = '3.5.0'
				""");

		PsiPropertyValueElement loc = findExtProperty(file, l -> "3.5.0".equals(l.propertyValue()));

		assertThat(loc).as("PropertyVersionLocation for ext dot-assignment").isNotNull();
		assertThat(loc.propertyKey()).isEqualTo("springVersion");
		assertThat(loc.propertyValue()).isEqualTo("3.5.0");
	}

	@Test
	void plainTopLevelAssignmentIsNotDetected() {

		PsiFile file = fixture.configureByText("build.gradle", """
				version = '1.2.3'
				group = 'com.example'
				""");

		assertThat(findExtProperty(file, l -> "1.2.3".equals(l.propertyValue())))
				.as("no hit for plain version = '...' assignment").isNull();
		assertThat(findExtProperty(file, l -> "com.example".equals(l.propertyValue())))
				.as("no hit for plain group = '...' assignment").isNull();
	}

	@Test
	void propertiesValueElementIsDetected() {

		PsiFile propsFile = fixture.configureByText("gradle.properties", """
				springVersion=3.5.0
				lombokVersion=1.18.36
				""");

		// Find an element that is inside the value of "springVersion"
		PsiElement valueElement = findPropertyValueElement(propsFile, "springVersion");

		assertThat(valueElement).as("value element for springVersion").isNotNull();
		PsiPropertyValueElement loc = GroovyDslUtils.findPropertiesVersionElement(valueElement);
		assertThat(loc).as("PropertyVersionLocation for springVersion").isNotNull();
		assertThat(loc.propertyKey()).isEqualTo("springVersion");
		assertThat(loc.propertyValue()).isEqualTo("3.5.0");
	}

	@Test
	void propertiesKeyElementIsNotDetected() {

		PsiFile propsFile = fixture.configureByText("gradle.properties", "springVersion=3.5.0\n");

		// Find an element that is inside the KEY "springVersion"
		PsiElement keyElement = findPropertyKeyElement(propsFile, "springVersion");

		assertThat(keyElement).as("key element for springVersion").isNotNull();
		PsiPropertyValueElement loc = GroovyDslUtils.findPropertiesVersionElement(keyElement);
		assertThat(loc).as("no PropertyVersionLocation for key element").isNull();
	}

	@Test
	void bomWithInterpolatedVersionLinksToExtPropertyDeclaration() {

		PsiFile file = fixture.configureByText("build.gradle", """
				ext {
				    set('springModulithVersion', "2.0.3")
				}

				dependencyManagement {
				    imports {
				        mavenBom "org.springframework.modulith:spring-modulith-bom:${springModulithVersion}"
				    }
				}
				""");

		List<DependencyAndVersionLocation> bomHits = findVersionLocations(file,
				loc -> loc.artifactId().groupId().startsWith("org.springframework.modulith"));
		assertThat(bomHits).as("exactly one hit for the spring-modulith-bom managed import").hasSize(1);

		DependencyAndVersionLocation bomLoc = bomHits.get(0);
		assertThat(bomLoc.artifactId().groupId()).isEqualTo("org.springframework.modulith");
		assertThat(bomLoc.artifactId().artifactId()).isEqualTo("spring-modulith-bom");
		assertThat(bomLoc.isPropertyReference()).as("version should be a property reference").isTrue();
		assertThat(bomLoc.dependency().getVersionSource()).as("property key stripped from ${…}")
				.isEqualTo(VersionSource.property("springModulithVersion"));

		PsiPropertyValueElement propLoc = findExtProperty(file, l -> "2.0.3".equals(l.propertyValue()));
		assertThat(propLoc).as("PropertyVersionLocation for the ext set() declaration").isNotNull();
		assertThat(propLoc.propertyKey()).isEqualTo("springModulithVersion");
		assertThat(propLoc.propertyValue()).isEqualTo("2.0.3");
	}

	private static <T> List<T> findAll(PsiFile file, Function<PsiElement, T> finder) {
		List<T> hits = new ArrayList<>();
		file.accept(new PsiRecursiveElementVisitor() {
			@Override
			public void visitElement(PsiElement element) {
				super.visitElement(element);
				T result = finder.apply(element);
				if (result != null) {
					hits.add(result);
				}
			}
		});
		return hits;
	}

	private static List<DependencyAndVersionLocation> findVersionLocations(PsiFile file,
			Predicate<DependencyAndVersionLocation> predicate) {
		return findAll(file, element -> {
			DependencyAndVersionLocation location = GroovyDslUtils.findGroovyVersionElement(element,
					GradlePropertyResolver.create(file));
			return (location != null && predicate.test(location)) ? location : null;
		});
	}

	private static DependencyAndVersionLocation findVersionLocation(PsiFile file,
			Predicate<DependencyAndVersionLocation> predicate) {
		return findVersionLocations(file, predicate).stream().findFirst().orElse(null);
	}

	private static PsiPropertyValueElement findExtProperty(PsiFile file,
			Predicate<PsiPropertyValueElement> predicate) {
		return findAll(file, GroovyDslUtils::findGroovyExtPropertyVersionElement).stream().filter(predicate).findFirst()
				.orElse(null);
	}

	private static PsiElement findPropertyValueElement(PsiFile propsFile, String key) {
		if (!(propsFile instanceof PropertiesFile pf)) {
			return null;
		}
		IProperty property = pf.findPropertyByKey(key);
		if (property == null) {
			return null;
		}
		// The value element is the last child of the IProperty PSI node.
		return property.getPsiElement().getLastChild();
	}

	private static PsiElement findPropertyKeyElement(PsiFile propsFile, String key) {
		if (!(propsFile instanceof PropertiesFile pf)) {
			return null;
		}
		IProperty property = pf.findPropertyByKey(key);
		if (property == null) {
			return null;
		}
		// The key element is the first child of the IProperty PSI node.
		return property.getPsiElement().getFirstChild();
	}

}
