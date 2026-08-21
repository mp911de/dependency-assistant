import org.jetbrains.intellij.platform.gradle.tasks.RunIdeTask
import org.jetbrains.intellij.platform.gradle.tasks.VerifyPluginTask
import org.jsoup.Jsoup
import kotlin.streams.asSequence

plugins {
	id("java")
	id("org.jetbrains.intellij.platform") version "2.16.0"
	id("org.asciidoctor.jvm.convert") version "4.0.5"
}

group = "biz.paluch"
version = "0.5.0-SNAPSHOT"

repositories {
	mavenCentral()
	intellijPlatform {
		defaultRepositories()
	}
}

// AsciidoctorJ CLI for rendering CHANGELOG.adoc; kept off the Asciidoctor Gradle plugin as
// it is neither configuration-cache compatible nor free of Gradle deprecations
val asciidoctorj = configurations.create("asciidoctorj")

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
	asciidoctorj("org.asciidoctor:asciidoctorj-cli:3.0.1")

	intellijPlatform {
		intellijIdea("2026.1.3")
		testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
		testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.JUnit5)
		bundledPlugin("org.jetbrains.idea.maven")
		bundledPlugin("org.jetbrains.plugins.gradle")
		bundledPlugin("com.intellij.gradle")
		bundledPlugin("org.intellij.groovy")
		bundledPlugin("org.jetbrains.kotlin")
		bundledPlugin("org.toml.lang")
		bundledPlugin("org.jetbrains.plugins.yaml")
		bundledModule("intellij.yaml.backend")
		bundledPlugin("org.jetbrains.plugins.github")
		bundledPlugin("Git4Idea")
		bundledPlugin("com.intellij.modules.json")
		bundledPlugin("org.jetbrains.security.package-checker")
	}

	implementation("org.springframework:spring-core:7.0.9")
	implementation("org.xmlbeam:xmlprojector:1.4.26")
	compileOnly("org.jspecify:jspecify:1.0.1")

	testImplementation("org.assertj:assertj-core:3.27.7")
	testImplementation("org.junit.jupiter:junit-jupiter:6.1.3")
	testImplementation("org.wiremock:wiremock-standalone:3.13.1")

	// https://youtrack.jetbrains.com/issue/IJPL-159134/JUnit5-Test-Framework-refers-to-JUnit4-java.lang.NoClassDefFoundError-junit-framework-TestCase
	testImplementation("junit:junit:4.13.2")

	testImplementation("com.tngtech.archunit:archunit-junit5-api:1.5.0")

	testRuntimeOnly("org.junit.platform:junit-platform-launcher")
	testRuntimeOnly("com.tngtech.archunit:archunit-junit5-engine:1.5.0")
}

intellijPlatform {
	pluginConfiguration {
		ideaVersion {
			sinceBuild = "261"
		}

		description =
			providers.fileContents(layout.projectDirectory.file("src/main/resources/META-INF/description.html")).asText

		// no lambda capturing the build script here, it would drag Project into the
		// configuration cache; patchPluginXml wires the producing task via dependsOn
		changeNotes = providers.fileContents(layout.buildDirectory.file("docs/asciidoc/CHANGELOG.html")).asText.map { html ->
			Jsoup.parse(html)
				.select("#releasenotes").get(0).nextElementSibling()!!.children()
				.take(20)
				.stream().map { e ->
					e.html()
						.replace(Regex("\\(work in progress\\)"), "")
						.replace(
							Regex("\\(preview, available from GitHub releases\\)"),
							""
						)
						.replace(
							Regex("#([0-9]+)"),
							"<a href=\"https://github.com/mp911de/dependency-assistant/issues/$1\">#$1</a>"
						)
						.replace(
							Regex("(?i)@([a-z\\d](?:[a-z\\d]|-(?=[a-z\\d])){0,38})"),
							"<a href=\"https://github.com/$1\">@$1</a>"
						)
				}
				.asSequence().joinToString("\n")
		}
	}

	pluginVerification {

		// fail only on hard compatibility problems, ignore warnings/experimental
		failureLevel = listOf(
			VerifyPluginTask.FailureLevel.COMPATIBILITY_PROBLEMS,
			VerifyPluginTask.FailureLevel.INTERNAL_API_USAGES,
			VerifyPluginTask.FailureLevel.SCHEDULED_FOR_REMOVAL_API_USAGES,
			VerifyPluginTask.FailureLevel.OVERRIDE_ONLY_API_USAGES,
			VerifyPluginTask.FailureLevel.NON_EXTENDABLE_API_USAGES,
			VerifyPluginTask.FailureLevel.PLUGIN_STRUCTURE_WARNINGS,
			VerifyPluginTask.FailureLevel.MISSING_DEPENDENCIES,
			VerifyPluginTask.FailureLevel.INVALID_PLUGIN
		)

		ides {
			recommended()
		}
	}
}

java {
	toolchain {
		languageVersion.set(JavaLanguageVersion.of(21))
	}
}

tasks {

	patchPluginXml {
		dependsOn("asciidoctor")
		sinceBuild = "253.25908"
		untilBuild = provider { null }
	}

	register<JavaExec>("asciidoctor") {
		description = "Renders CHANGELOG.adoc to HTML for the plugin change notes."
		group = "documentation"

		val changelog = layout.projectDirectory.file("CHANGELOG.adoc")
		val html = layout.buildDirectory.file("docs/asciidoc/CHANGELOG.html")
		val revnumber = project.version.toString()
		inputs.file(changelog)
		outputs.file(html)

		classpath = asciidoctorj
		mainClass = "org.asciidoctor.cli.jruby.AsciidoctorInvoker"
		// 🤐🔇 JRuby FilenoUtil warning about JDK IO subsystem access
		jvmArgs("--add-opens", "java.base/sun.nio.ch=ALL-UNNAMED", "--add-opens", "java.base/java.io=ALL-UNNAMED")
		argumentProviders.add(CommandLineArgumentProvider {
			listOf(
				"--safe-mode", "unsafe",
				"--attribute", "revnumber=$revnumber",
				"--out-file", html.get().asFile.absolutePath,
				changelog.asFile.absolutePath
			)
		})
	}

	withType<Test>().configureEach {
		useJUnitPlatform()
		failOnNoDiscoveredTests = true
	}

	withType<RunIdeTask>().configureEach {
		systemProperty("idea.log.debug.categories", "biz.paluch.dap")
	}
}
