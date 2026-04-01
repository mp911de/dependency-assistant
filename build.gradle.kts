plugins {
	id("java")
	id("org.jetbrains.intellij.platform") version "2.13.1"
}

group = "biz.paluch"
version = "0.5.0-M2"

repositories {
	mavenCentral()
	intellijPlatform {
		defaultRepositories()
	}
}

// Read more: https://plugins.jetbrains.com/docs/intellij/tools-intellij-platform-gradle-plugin.html
dependencies {
	intellijPlatform {
		intellijIdea("2025.3.1")
		testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.JUnit5)
		bundledPlugin("org.jetbrains.idea.maven")
	}
	implementation("org.springframework:spring-core:7.0.6")
	implementation("org.xmlbeam:xmlprojector:1.4.26")
	compileOnly("org.jspecify:jspecify:1.0.0")

	testImplementation("org.assertj:assertj-core:3.27.7")
	testImplementation("org.junit.jupiter:junit-jupiter:6.0.3")
	testRuntimeOnly("org.junit.platform:junit-platform-launcher")

	// https://youtrack.jetbrains.com/issue/IJPL-159134/JUnit5-Test-Framework-refers-to-JUnit4-java.lang.NoClassDefFoundError-junit-framework-TestCase
	testRuntimeOnly("junit:junit:4.13.2")
}

intellijPlatform {
	pluginConfiguration {
		ideaVersion {
			sinceBuild = "253"
		}

		changeNotes = """
            Initial version
        """.trimIndent()
	}

	pluginVerification {
		ides {
			recommended()
		}
	}
}

tasks {
	withType<JavaCompile> {
		sourceCompatibility = "17"
		targetCompatibility = "17"
	}
	withType<Test>().configureEach {
		useJUnitPlatform()
		failOnNoDiscoveredTests = true
	}
}
