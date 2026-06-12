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

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.artifact.UpgradeStrategy;
import com.intellij.json.psi.JsonArray;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.json.psi.JsonValue;
import com.intellij.openapi.diagnostic.Logger;
import org.jspecify.annotations.Nullable;

/**
 * Parses a {@code dependencyfile.json} descriptor into a {@link Rules}
 * resolution view.
 *
 * <p>Parsing is lenient: an artifact or branch entry that cannot be interpreted
 * is logged and skipped rather than failing the whole descriptor, so a
 * partially valid file still yields the rules it can express. Generation values
 * (a single string or an array of strings) are salvaged entry by entry: invalid
 * entries are logged and dropped, and a rule whose generations all fail to
 * parse stays present but unconstrained. Unknown upgrade strategies are
 * ignored. A descriptor whose top-level value is not a JSON object resolves to
 * {@link Rules#absent()}.
 *
 * <p>The parser reads PSI and must be invoked inside a read action.
 *
 * @author Mark Paluch
 * @see Rules
 * @see DependencyfileService
 */
class RuleParser {

	private static final Logger LOG = Logger.getInstance(RuleParser.class);

	private final JsonFile file;

	/**
	 * Create a parser for the given descriptor.
	 * @param file the {@code dependencyfile.json} PSI file to parse; must not be
	 * {@literal null}.
	 */
	RuleParser(JsonFile file) {
		this.file = file;
	}

	/**
	 * Parse the descriptor into a resolution view, skipping any individual rule
	 * that cannot be interpreted.
	 *
	 * @return the parsed rules, or {@link Rules#absent()} when the descriptor has
	 * no JSON object root; never {@literal null}.
	 */
	Rules parse() {

		if (!(file.getTopLevelValue() instanceof JsonObject root)) {
			return Rules.absent();
		}

		DependencyRules.Builder builder = DependencyRules.builder();

		object(root.findProperty("artifacts")).forEach(artifact -> addArtifact(builder, artifact));
		object(root.findProperty("branches")).forEach(branch -> addBranch(builder, branch));
		return builder.build();
	}

	private void addArtifact(DependencyRules.Builder builder, JsonProperty artifact) {

		try {
			builder.artifact(artifact.getName(), rule -> applyArtifact(rule, artifact.getValue()));
		} catch (RuntimeException ex) {
			LOG.warn("Ignoring invalid artifact rule '%s' in %s".formatted(artifact.getName(), file.getName()), ex);
		}
	}

	private void addBranch(DependencyRules.Builder builder, JsonProperty branch) {

		if (!(branch.getValue() instanceof JsonObject definition)) {
			return;
		}

		try {
			builder.branch(branch.getName(), rule -> {

				if (definition.findProperty("upgrades") instanceof JsonProperty upgrades
						&& upgrades.getValue() instanceof JsonArray array) {
					rule.upgrades(upgradeStrategies(array));
				}
				object(definition.findProperty("artifacts"))
						.forEach(artifact -> rule.artifact(artifact.getName(),
								nested -> applyArtifact(nested, artifact.getValue())));
			});
		} catch (RuntimeException ex) {
			LOG.warn("Ignoring invalid branch rule '%s' in %s".formatted(branch.getName(), file.getName()), ex);
		}
	}

	private void applyArtifact(DependencyRules.ArtifactRuleBuilder rule, @Nullable JsonValue value) {

		if (value instanceof JsonObject definition) {

			String name = string(definition.findProperty("name"));
			if (name != null) {
				rule.name(name);
			}

			JsonProperty generation = definition.findProperty("generation");
			if (generation != null) {
				rule.generation(generations(generation.getValue()));
			}
			return;
		}
		rule.generation(generations(value));
	}

	/**
	 * Read a generation value (a single string or an array of strings) into
	 * {@link Generations}, dropping entries that are not valid generations with
	 * a warning. When no valid entry survives, the result is
	 * {@linkplain Generations#unconstrained() unconstrained} rather than a
	 * parse failure, so a typo never silently drops the whole rule.
	 */
	private Generations generations(@Nullable JsonValue value) {

		List<String> entries = new ArrayList<>();
		if (value instanceof JsonArray array) {
			for (JsonValue element : array.getValueList()) {
				addGeneration(entries, element);
			}
		} else {
			addGeneration(entries, value);
		}

		return Generations.from(entries.toArray(String[]::new));
	}

	private void addGeneration(List<String> entries, @Nullable JsonValue source) {

		String entry = string(source);
		if (entry == null) {
			if (source != null) {
				LOG.warn("Ignoring non-string generation '%s' in %s".formatted(source.getText(), file.getName()));
			}
			return;
		}

		// "*" is the Generations-level wildcard collapsing to unconstrained, not a
		// single Generation
		if (!"*".equals(entry)) {
			try {
				Generation.of(entry);
			} catch (RuntimeException ex) {
				LOG.warn("Ignoring invalid generation '%s' in %s".formatted(entry, file.getName()));
				return;
			}
		}
		entries.add(entry);
	}

	private UpgradeStrategy[] upgradeStrategies(JsonArray array) {

		List<UpgradeStrategy> strategies = new ArrayList<>();
		for (JsonValue value : array.getValueList()) {

			UpgradeStrategy strategy = upgradeStrategy(string(value));
			if (strategy != null) {
				strategies.add(strategy);
			}
		}
		return strategies.toArray(UpgradeStrategy[]::new);
	}

	private @Nullable UpgradeStrategy upgradeStrategy(@Nullable String value) {

		if (value == null) {
			return null;
		}
		try {
			return UpgradeStrategy.valueOf(value.toUpperCase());
		} catch (IllegalArgumentException ex) {
			LOG.warn("Ignoring unknown upgrade strategy '%s' in %s".formatted(value, file.getName()));
			return null;
		}
	}

	private static List<JsonProperty> object(@Nullable JsonProperty property) {
		return property != null && property.getValue() instanceof JsonObject object ? object.getPropertyList()
				: List.of();
	}

	private static @Nullable String string(@Nullable JsonProperty property) {
		return property != null ? string(property.getValue()) : null;
	}

	private static @Nullable String string(@Nullable JsonValue value) {
		return value instanceof JsonStringLiteral literal ? literal.getValue() : null;
	}

}
