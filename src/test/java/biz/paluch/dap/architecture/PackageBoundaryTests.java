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

package biz.paluch.dap.architecture;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Architecture tests that enforce the cross-package import boundary required
 * between the YAML {@code github} updater and the JSON {@code npm} updater.
 *
 * <p>The {@code github} package may not depend on {@code npm}. The {@code npm}
 * package may only depend on the {@linkplain #ALLOWED_GITHUB_TYPES allowed
 * GitHub types}, namely {@code GitHubReleaseSource} (release lookup for
 * Git-backed dependency entries). Shared Git URL parsing now lives on
 * {@code biz.paluch.dap.artifact.GitRepositoryMetadata} and is not subject to
 * the GitHub allow-list.
 *
 * @author Mark Paluch
 */
class PackageBoundaryTests {

	private static final Path SOURCE_ROOT = resolveSourceRoot();

	private static final Set<String> ALLOWED_GITHUB_TYPES = Set.of(
			"biz.paluch.dap.github.GitHubReleaseSource");

	@Test
	void sourceRootResolves() {
		assertThat(SOURCE_ROOT).as("resolved source root").exists();
	}

	@Test
	void githubPackageDoesNotImportNpm() throws IOException {

		List<String> violations = collectImports(SOURCE_ROOT.resolve("github"), "biz.paluch.dap.npm");

		assertThat(violations).as("github → npm imports").isEmpty();
	}

	@Test
	void npmPackageOnlyImportsAllowedGithubTypes() throws IOException {

		List<String> violations = new ArrayList<>();
		Path npmRoot = SOURCE_ROOT.resolve("npm");
		try (Stream<Path> stream = Files.walk(npmRoot)) {
			stream.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
				try {
					String content = Files.readString(file, StandardCharsets.UTF_8);
					for (String line : content.split("\\R")) {
						String trimmed = line.trim();
						if (!trimmed.startsWith("import ")) {
							continue;
						}
						if (!trimmed.contains("biz.paluch.dap.github.")) {
							continue;
						}
						String fqn = trimmed.substring("import ".length()).replace(";", "").trim();
						String enclosing = stripTrailing(fqn);
						if (!ALLOWED_GITHUB_TYPES.contains(enclosing)) {
							violations.add(file.getFileName() + ": " + fqn);
						}
					}
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			});
		}

		assertThat(violations).as("npm → github imports outside the allowlist").isEmpty();
	}

	private static List<String> collectImports(Path root, String forbidden) throws IOException {

		List<String> hits = new ArrayList<>();
		try (Stream<Path> stream = Files.walk(root)) {
			stream.filter(p -> p.toString().endsWith(".java")).forEach(file -> {
				try {
					String content = Files.readString(file, StandardCharsets.UTF_8);
					for (String line : content.split("\\R")) {
						String trimmed = line.trim();
						if (trimmed.startsWith("import ") && trimmed.contains(forbidden)) {
							hits.add(file.getFileName() + ": " + trimmed);
						}
					}
				} catch (IOException ex) {
					throw new RuntimeException(ex);
				}
			});
		}
		return hits;
	}

	/**
	 * Resolve the production-source root from {@code user.dir} so the test does not
	 * depend on a Gradle-specific working directory. The walk in
	 * {@link Files#walk(Path, java.nio.file.FileVisitOption...) Files.walk} fails
	 * with a clear {@code NoSuchFileException} if the resolved root is missing, and
	 * the {@code sourceRootResolves} sanity test asserts the path up front.
	 */
	private static Path resolveSourceRoot() {
		return Path.of(System.getProperty("user.dir"), "src/main/java/biz/paluch/dap");
	}

	/**
	 * Drop trailing nested-type segments from an FQN so {@code Foo.Bar.Baz} maps
	 * back to its enclosing top-level type.
	 */
	private static String stripTrailing(String fqn) {

		String[] parts = fqn.split("\\.");
		StringBuilder result = new StringBuilder();
		for (String part : parts) {
			if (result.length() > 0) {
				result.append('.');
			}
			result.append(part);
			if (!part.isEmpty() && Character.isUpperCase(part.charAt(0))) {
				return result.toString();
			}
		}
		return fqn;
	}

}
