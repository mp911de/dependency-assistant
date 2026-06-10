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

package biz.paluch.dap.rule;

import java.util.function.Predicate;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.extension.IdeaProjectTests;
import biz.paluch.dap.extension.ProjectFile;
import com.intellij.json.psi.JsonFile;
import com.intellij.psi.PsiFile;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * PSI-level integration tests for {@link RuleParser}.
 *
 * @author Mark Paluch
 */
@IdeaProjectTests
class RuleParserTests {

	private static final ArtifactId SPRING_CORE = ArtifactId.of("org.springframework", "spring-core");

	@Test
	@ProjectFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:*": "7.0.x"
			  }
			}
			""")
	void parsesScalarArtifactGeneration(PsiFile file) {

		DependencyRule rule = parse(file).resolve(SPRING_CORE, "main", null);

		assertThat(rule.getGeneration()).isEqualTo("7.0");
		assertThat(rule).accepts(ArtifactVersion.of("7.0.1"))
				.rejects(ArtifactVersion.of("8.0.0"));
	}

	@Test
	@ProjectFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:*": { "name": "Spring Framework", "generation": "7.0.x" }
			  }
			}
			""")
	void parsesObjectArtifactGeneration(PsiFile file) {

		DependencyRule rule = parse(file).resolve(SPRING_CORE, "main", null);

		assertThat(rule.getGeneration()).isEqualTo("7.0");
		assertThat(rule).accepts(ArtifactVersion.of("7.0.1"));
	}

	@Test
	@ProjectFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "*": "1",
			    "org.springframework:*": "6.0",
			    "spring-*": "6.1",
			    "org.springframework:spring-core": "6.2"
			  }
			}
			""")
	void resolvesMostSpecificArtifactRule(PsiFile file) {

		DependencyRule rule = parse(file).resolve(SPRING_CORE, "main", null);

		assertThat(rule.getGeneration()).isEqualTo("6.2");
	}

	@Test
	@ProjectFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:*": "7.0",
			    "org.junit:*": "5.13"
			  },
			  "branches": {
			    "3.5.x": {
			      "artifacts": { "org.springframework:*": "6.0" }
			    }
			  }
			}
			""")
	void branchRuleOverridesDefaultsAndInheritsArtifacts(PsiFile file) {

		Rules rules = parse(file);

		assertThat(rules.resolve(SPRING_CORE, "3.5.x", null)
				.getGeneration()).isEqualTo("6.0");
		assertThat(rules.resolve(ArtifactId.of("org.junit", "junit-bom"), "3.5.x", null)
				.getGeneration()).isEqualTo("5.13");
		assertThat(rules.resolve(SPRING_CORE, "main", null)
				.getGeneration()).isEqualTo("7.0");
	}

	@Test
	@ProjectFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:*": "7.0"
			  },
			  "branches": {
			    "2.*.x": {
			      "upgrades": ["patch", "minor"],
			      "artifacts": { "org.springframework:*": "5.0" }
			    }
			  }
			}
			""")
	void parsesBranchUpgradeStrategies(PsiFile file) {

		DependencyRule rule = parse(file).resolve(SPRING_CORE, "2.5.x", null);
		Predicate<UpgradeStrategy> isEnabled = rule::isEnabled;

		assertThat(rule.getGeneration()).isEqualTo("5.0");
		assertThat(isEnabled).accepts(UpgradeStrategy.PATCH, UpgradeStrategy.MINOR)
				.rejects(UpgradeStrategy.MAJOR);
	}

	@Test
	@ProjectFile(name = "dependencyfile.json", content = """
			{
			  "branches": {
			    "2.*.x": {
			      "upgrades": ["patch", "bogus"],
			      "artifacts": { "org.springframework:*": "5.0" }
			    }
			  }
			}
			""")
	void skipsUnknownUpgradeStrategy(PsiFile file) {

		DependencyRule rule = parse(file).resolve(SPRING_CORE, "2.5.x", null);
		Predicate<UpgradeStrategy> isEnabled = rule::isEnabled;

		assertThat(isEnabled).accepts(UpgradeStrategy.PATCH)
				.rejects(UpgradeStrategy.MINOR, UpgradeStrategy.MAJOR);
	}

	@Test
	@ProjectFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:*": "not-a-version",
			    "org.junit:*": "5.0"
			  }
			}
			""")
	void skipsInvalidGenerationButKeepsValidRules(PsiFile file) {

		Rules rules = parse(file);

		assertThat(rules.resolve(SPRING_CORE, "main", null)).isSameAs(DependencyRule.absent());
		assertThat(rules.resolve(ArtifactId.of("org.junit", "junit-bom"), "main", null)
				.getGeneration())
				.isEqualTo("5.0");
	}

	@Test
	@ProjectFile(name = "dependencyfile.json", content = "\"not an object\"")
	void resolvesNonObjectRootToAbsentRules(PsiFile file) {

		DependencyRule rule = parse(file).resolve(SPRING_CORE, "main", null);

		assertThat(rule).isSameAs(DependencyRule.absent());
		assertThat(rule.getGeneration()).isEmpty();
	}

	@Test
	@ProjectFile(name = "dependencyfile.json", content = """
			{
			  "artifacts": {
			    "org.springframework:*": { "name": "Spring Framework", "generation": "7.0.x" }
			  },
			  "branches": {
			    "3.5.x": {
			      "artifacts": {
			        "org.springframework:*": "6.0",
			        "spring-security": "7.0.x"
			      }
			    },
			    "2.*.x": {
			      "upgrades": ["patch", "minor"],
			      "artifacts": { "org.springframework:*": "5.0" }
			    }
			  }
			}
			""")
	void parsesFullDescriptor(PsiFile file) {

		Rules rules = parse(file);

		assertThat(rules.resolve(SPRING_CORE, "main", null)
				.getGeneration()).isEqualTo("7.0");
		assertThat(rules.resolve(SPRING_CORE, "3.5.x", null)
				.getGeneration()).isEqualTo("6.0");
		assertThat(rules.resolve(ArtifactId.of("org.springframework.security", "spring-security"), "3.5.x", null)
				.getGeneration()).isEqualTo("7.0");
		assertThat(rules.resolve(SPRING_CORE, "2.5.x", null)
				.getGeneration()).isEqualTo("5.0");
	}

	private static Rules parse(PsiFile file) {
		return new RuleParser((JsonFile) file).parse();
	}

}
