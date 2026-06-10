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

package biz.paluch.dap.assistant;

import java.util.ArrayList;
import java.util.List;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.InterfaceAssistant;
import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.UpgradeStrategy;
import biz.paluch.dap.rule.DependencyRule;
import biz.paluch.dap.support.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.icons.AllIcons;

/**
 * Outcome of testing a {@link DependencyRule} against a concrete artifact version, paired with
 * the presentation an upgrade view needs to surface it.
 *
 * <p>The rule is evaluated once at construction and the result is retained as one of three
 * states: undefined (no rule governs the artifact), passed, or not passed. A renderer consults
 * {@link #isPresent()} to decide whether to show an indicator at all, then {@link #getIcon()}
 * and {@link #getToolTipText()} for the indicator itself.
 *
 * <p>{@link #absent()} is a distinct sentinel for "the project defines rules, but none govern
 * this artifact": unlike an undefined evaluation it reports {@link #isPresent() present} and
 * renders a neutral icon with an explanatory tooltip. {@link DependencyUpgradeReview#getResult}
 * substitutes it when the project has rules yet the candidate is ungoverned.
 *
 * @author Mark Paluch
 */
class EvaluatedDependencyRule {

	private static final EvaluatedDependencyRule ABSENT = new EvaluatedDependencyRule(DependencyRule.absent(),
			ArtifactId.of("", ""), ArtifactVersion.of("1.0"), "") {

		@Override
		public boolean isPresent() {
			return true;
		}

		@Override
		public Icon getIcon() {
			return AllIcons.Ide.Readwrite;
		}

		@Override
		public String getToolTipText() {
			return MessageBundle.message("inspection.dependency-rule.absent");
		}

	};

	private static final UpgradeStrategy[] STRATEGIES = UpgradeStrategy.values();

	private final DependencyRule rule;

	private final ArtifactId artifactId;

	private final String renderedVersion;

	private final EvaluationState result;

	private EvaluatedDependencyRule(DependencyRule rule, ArtifactId artifactId, ArtifactVersion version,
			InterfaceAssistant assistant) {
		this(rule, artifactId, version, assistant.getDocumentationText(version));
	}

	private EvaluatedDependencyRule(DependencyRule rule, ArtifactId artifactId, ArtifactVersion version, String renderedVersion) {
		this.rule = rule;
		this.artifactId = artifactId;
		this.renderedVersion = renderedVersion;

		if (rule.isPresent()) {
			this.result = rule.test(version) ? EvaluationState.PASSED : EvaluationState.NOT_PASSED;
		}
		else {
			this.result = EvaluationState.UNDEFINED;
		}
	}

	/**
	 * Evaluate the governing rule of the given candidate against its current version.
	 * @return the evaluation outcome for the candidate's current version.
	 */
	public static EvaluatedDependencyRule of(DependencyRule rule, ArtifactId artifactId, ArtifactVersion version, InterfaceAssistant assistant) {
		return new EvaluatedDependencyRule(rule, artifactId, version,
				assistant);
	}

	/**
	 * Return the shared sentinel for an artifact that no rule governs while the project still
	 * defines rules.
	 *
	 * @return a sentinel that reports {@link #isPresent() present} with a neutral icon and an
	 * explanatory tooltip.
	 */
	public static EvaluatedDependencyRule absent() {
		return ABSENT;
	}

	/**
	 * Return the gutter icon for this outcome.
	 *
	 * @return the icon for a passed rule, otherwise the warning icon.
	 */
	public Icon getIcon() {
		if (result == EvaluationState.PASSED) {
			if (isLocked()) {
				return DependencyAssistantIcons.DEPENDENCY_LOCK;
			}
			return DependencyAssistantIcons.DEPENDENCY_RULE;
		}
		return DependencyAssistantIcons.DEPENDENCY_RULE_WARN;
	}

	/**
	 * Return whether this evaluation contributes an indicator.
	 *
	 * @return {@literal true} when a rule governs the artifact, whether passed or not passed;
	 * {@literal false} when no rule is defined.
	 */
	public boolean isPresent() {
		return result != EvaluationState.UNDEFINED;
	}

	/**
	 * Build the HTML tooltip describing the rule outcome and the enabled upgrade strategies.
	 *
	 * @return the tooltip markup; empty when there is nothing to report.
	 */
	public String getToolTipText() {

		StringBuilder sb = new StringBuilder();

		if (isLocked()) {

			if (result == EvaluationState.NOT_PASSED) {
				sb.append(MessageBundle.message("inspection.dependency-rule.problem",
						artifactId.artifactId(), renderedVersion, rule.getGeneration()));
			}

			if (result == EvaluationState.PASSED) {
				sb.append(MessageBundle.message("inspection.dependency-rule.description",
						artifactId.artifactId(), rule.getGeneration()));
			}
		}

		String strategies = getUpgradeStrategiesHint();

		if (StringUtils.hasText(strategies)) {
			if (!sb.isEmpty()) {
				sb.append("<br>");
			}
			sb.append(strategies);
		}

		return sb.toString();
	}

	private boolean isLocked() {
		return StringUtils.hasText(rule.getGeneration());
	}

	private String getUpgradeStrategiesHint() {

		int strategyCount = 0;

		StringBuilder strategies = new StringBuilder()
				.append(MessageBundle.message("inspection.dependency-rule.strategies"))
				.append(" ");

		List<String> names = new ArrayList<>();
		for (UpgradeStrategy strategy : STRATEGIES) {
			if (rule.isEnabled(strategy)) {
				strategyCount++;
				names.add(MessageBundle.message("upgrade-strategy." + strategy.name()));
			}
		}

		strategies.append(String.join(", ", names));
		strategies.append(".");

		if (strategyCount != 0 && strategyCount != STRATEGIES.length) {
			return strategies.toString();
		}

		return "";
	}


	enum EvaluationState {
		UNDEFINED, PASSED, NOT_PASSED;
	}

}
