/*
 * Copyright 2026-present the original author or authors.
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
import java.util.function.Predicate;

import javax.swing.Icon;

import biz.paluch.dap.DependencyAssistantIcons;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.support.UpgradeStrategy;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.util.text.HtmlBuilder;
import com.intellij.openapi.util.text.HtmlChunk;
import com.intellij.openapi.util.text.StringUtil;

/**
 * Evaluated Dependency Rule for one concrete artifact version.
 *
 * <p>The outcome is absent, satisfied, or violated. The evaluator retains the
 * governing {@link DependencyRule} so callers can test candidate versions and
 * render the same governance state without resolving the rule again.
 *
 * @author Mark Paluch
 */
public class DependencyRuleEvaluator implements Predicate<ArtifactVersion> {

	private static final DependencyRuleEvaluator ABSENT = new DependencyRuleEvaluator(DependencyRule.absent(),
			ArtifactVersion.of("1.0")) {

		@Override
		public boolean isPresent() {
			return false;
		}

		@Override
		public Icon getIcon() {
			return AllIcons.Ide.Readwrite;
		}

		@Override
		public HtmlChunk getToolTipText(String displayName) {
			return HtmlChunk.text(MessageBundle.message("inspection.dependency-rule.absent"));
		}

	};

	private static final UpgradeStrategy[] STRATEGIES = UpgradeStrategy.values();

	private final DependencyRule rule;

	private final ArtifactVersion version;

	private final EvaluationState result;

	private DependencyRuleEvaluator(DependencyRule rule, ArtifactVersion version) {
		this.rule = rule;
		this.version = version;

		if (rule.isPresent()) {
			this.result = rule.test(version) ? EvaluationState.PASSED : EvaluationState.NOT_PASSED;
		} else {
			this.result = EvaluationState.UNDEFINED;
		}
	}

	/**
	 * Evaluate the governing rule of the given candidate against its current
	 * version.
	 *
	 * @param rule the governing dependency rule.
	 * @param version the version to evaluate.
	 * @return the evaluation outcome for the candidate's current version.
	 */
	public static DependencyRuleEvaluator create(DependencyRule rule, ArtifactVersion version) {
		return new DependencyRuleEvaluator(rule, version);
	}

	/**
	 * Return the shared sentinel for an unavailable or ungoverned evaluation.
	 *
	 * @return a sentinel that reports {@link #isPresent() not present} with a
	 * neutral icon and an explanatory tooltip.
	 */
	public static DependencyRuleEvaluator absent() {
		return ABSENT;
	}

	@Override
	public boolean test(ArtifactVersion artifactVersion) {
		return rule.test(artifactVersion);
	}

	/**
	 * Return the governing rule retained by this evaluation.
	 *
	 * @return the governing dependency rule.
	 */
	public DependencyRule getRule() {
		return rule;
	}

	/**
	 * Return the gutter icon for this outcome.
	 *
	 * @return the compliant or lock icon for a satisfied rule, the warning icon for
	 * a violated rule, or the neutral icon for the absent evaluation.
	 */
	public Icon getIcon() {
		if (isPassed()) {
			if (isLocked()) {
				return DependencyAssistantIcons.DEPENDENCY_LOCK;
			}
			return DependencyAssistantIcons.RULE_COMPLIANT;
		}
		return DependencyAssistantIcons.DEPENDENCY_RULE_WARN;
	}

	public boolean isPassed() {
		return result == EvaluationState.PASSED;
	}

	/**
	 * Return whether this evaluation contributes an indicator.
	 *
	 * @return {@literal true} when a rule governs the artifact, whether passed or
	 * not passed; {@literal false} when no rule is defined.
	 */
	public boolean isPresent() {
		return result != EvaluationState.UNDEFINED;
	}

	/**
	 * Return whether semantic version upgrading governs the evaluated rule.
	 *
	 * @return {@literal true} if semVer upgrading is the active governance mode;
	 * {@literal false} otherwise.
	 * @see DependencyRule#isSemanticUpgradingEnabled()
	 */
	public boolean isSemanticUpgradingEnabled() {
		return rule.isSemanticUpgradingEnabled();
	}

	public boolean isEnabled(UpgradeStrategy strategy) {
		return rule.isEnabled(strategy);
	}

	/**
	 * Render the tool tip describing the rule outcome and whether semantic
	 * upgrading is enabled.
	 *
	 * <p>Version and generation text are escaped. {@code displayName} is embedded
	 * as supplied and must already be safe for HTML.
	 *
	 * @param displayName the HTML-safe dependency display name.
	 * @return the tooltip fragment, possibly empty when no detail applies.
	 */
	public HtmlChunk getToolTipText(String displayName) {

		HtmlBuilder tooltip = new HtmlBuilder();

		if (isLocked()) {

			HtmlChunk version = HtmlChunk.text(this.version.toDocumentationString());
			HtmlChunk generations = HtmlChunk.text(rule.getGenerations().value());

			if (result == EvaluationState.NOT_PASSED) {
				tooltip.appendRaw(MessageBundle.message("inspection.dependency-rule.problem",
						StringUtil.escapeXmlEntities(displayName), version, generations));
			}

			if (isPassed()) {
				tooltip.appendRaw(MessageBundle.message("inspection.dependency-rule.description",
						StringUtil.escapeXmlEntities(displayName), generations));
			}
		}

		if (isSemanticUpgradingEnabled()) {

			if (!tooltip.isEmpty()) {
				tooltip.br();
			}
			tooltip.append(MessageBundle.message("inspection.dependency-rule.semantic-upgrade.enabled"));
		}

		return tooltip.toFragment();
	}

	/**
	 * Return the localized accessible description derived from the rule outcome,
	 * enabled non-remediation strategies, or semver governance mode.
	 *
	 * @return the derived accessible description, or an empty string when no
	 * description applies.
	 */
	public String getAccessibleName() {

		if (isLocked()) {
			if (result == EvaluationState.NOT_PASSED) {
				return MessageBundle.message("inspection.dependency-rule.display-name");
			}
			if (isPassed()) {
				return MessageBundle.message("inspection.dependency-rule.passed");
			}
		}

		String strategies = getUpgradeStrategiesHint();
		if (StringUtils.hasText(strategies)) {
			return strategies;
		} else if (isSemanticUpgradingEnabled()) {
			return MessageBundle.message("inspection.dependency-rule.semantic-upgrade.enabled");
		}

		return "";
	}

	/**
	 * Return whether constrained Generations govern the evaluated dependency.
	 *
	 * @return {@literal true} when the dependency is locked to at least one
	 * generation; {@literal false} when generations are unconstrained.
	 */
	public boolean isLocked() {
		return rule.getGenerations().isConstrained();
	}

	private String getUpgradeStrategiesHint() {

		int strategyCount = 0;

		StringBuilder strategies = new StringBuilder()
				.append(MessageBundle.message("inspection.dependency-rule.strategies"))
				.append(" ");

		List<String> names = new ArrayList<>();
		for (UpgradeStrategy strategy : STRATEGIES) {
			if (!strategy.isRemediation() && rule.isEnabled(strategy)) {
				strategyCount++;
				names.add(strategy.getDisplayName());
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
