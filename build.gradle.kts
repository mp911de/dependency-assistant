plugins {
	id("java")
	id("org.jetbrains.intellij.platform") version "2.10.5"
}

group = "com.sample"
version = "1.0-SNAPSHOT"

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
		testFramework(org.jetbrains.intellij.platform.gradle.TestFrameworkType.Platform)
		bundledPlugin("org.jetbrains.idea.maven")
	}
	implementation("org.springframework:spring-core:7.0.6")
	implementation("org.xmlbeam:xmlprojector:1.4.26")
	compileOnly("org.jspecify:jspecify:1.0.0")
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
}

tasks {
	withType<JavaCompile> {
		sourceCompatibility = "21"
		targetCompatibility = "21"
	}
}

