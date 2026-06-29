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

package biz.paluch.dap.maven.wrapper;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.function.Predicate;
import java.util.regex.Matcher;

import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.CredentialsInUrl;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.ImproperGroupId;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.InconsistentArtifact;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.InconsistentVersion;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.InvalidUrl;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.MalformedFileName;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.MissingChecksum;
import biz.paluch.dap.maven.wrapper.MavenWrapperUrlProblem.UnknownArtifact;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.PropertyUtils;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemHighlightType;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ProblemsHolder.ProblemBuilder;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;
import org.jspecify.annotations.Nullable;

/**
 * Thin PSI adapter that runs {@link MavenWrapperUrlAnalyzer} over Maven wrapper
 * property files and reports each detected {@link MavenWrapperUrlProblem} as a
 * warning with problem-specific quick fixes.
 *
 * @author Mark Paluch
 */
public class MavenWrapperUrlInspection extends LocalInspectionTool implements DumbAware {

	@Override
	public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {

		if (!MavenWrapperUtils.isWrapperFile(holder.getFile())) {
			return PsiElementVisitor.EMPTY_VISITOR;
		}

		StateService stateService = StateService.getInstance(holder.getProject());

		return new PsiElementVisitor() {

			@Override
			public void visitElement(PsiElement element) {

				if (!(element instanceof PropertyImpl property)) {
					return;
				}

				WrapperProperty kind = WrapperProperty.forKey(property.getUnescapedKey());
				String decodedValue = property.getUnescapedValue();
				TextRange rangeInElement = PropertyUtils.valueRangeInElement(property);
				if (kind == null || StringUtils.isEmpty(decodedValue) || rangeInElement == null) {
					return;
				}

				List<MavenWrapperUrlProblem> problems = MavenWrapperUrlAnalyzer.analyze(kind, decodedValue,
						property.getText());
				if (problems.isEmpty()) {
					registerMissingChecksum(holder, property, rangeInElement, kind, decodedValue);
					return;
				}

				String latestVersion = resolveLatestNonPreviewVersion(stateService, kind);

				for (MavenWrapperUrlProblem problem : problems) {
					registerProblem(holder, property, rangeInElement, kind, problem, latestVersion);
				}
			}

		};
	}

	private static void registerMissingChecksum(ProblemsHolder holder, PropertyImpl property, TextRange rangeInElement,
			WrapperProperty kind, String decodedValue) {

		if (!TrustedProjects.isProjectTrusted(holder.getProject())
				|| !MavenWrapperUrlAnalyzer.isChecksumCandidate(decodedValue, property.getText())
				|| !(property.getContainingFile() instanceof PropertiesFile properties)
				|| properties.findPropertyByKey(kind.shaKey()) != null) {
			return;
		}

		registerProblem(holder, property, rangeInElement, kind, new MissingChecksum(kind), null);
	}

	private static void registerProblem(ProblemsHolder holder, PropertyImpl property, TextRange rangeInElement,
			WrapperProperty kind, MavenWrapperUrlProblem problem, @Nullable String latestVersion) {

		for (TextRange range : rangesFor(property, rangeInElement, problem)) {
			ProblemBuilder builder = holder.problem(property, problem.getMessage()).range(range);

			if (problem instanceof MissingChecksum) {
				builder.highlight(ProblemHighlightType.WEAK_WARNING);
				if (TrustedProjects.isProjectTrusted(holder.getProject())) {
					builder.fix(new MavenWrapperChecksumQuickFix(kind));
				}
			} else {
				problem.getFixes(kind).forEach(builder::fix);
			}

			if (latestVersion != null) {
				builder.fix(MavenWrapperUrlFixes.useDefaultUrl(kind, latestVersion));
			}

			builder.register();
		}
	}

	private static List<TextRange> rangesFor(PropertyImpl property, TextRange fallback,
			MavenWrapperUrlProblem problem) {

		List<TextRange> ranges = switch (problem) {
		case CredentialsInUrl ignored -> credentialsRange(property);
		case InvalidUrl ignored -> List.of();
		case InconsistentVersion ignored -> coordinateRanges(property, "version1", "version2");
		case InconsistentArtifact ignored -> coordinateRanges(property, "artifactId1", "artifactId2");
		case ImproperGroupId(String actualGroupPath) -> improperGroupRange(property, actualGroupPath);
		case UnknownArtifact(String actualArtifactId) -> unknownArtifactRanges(property, actualArtifactId);
		case MalformedFileName(String actualFileName, String ignored) -> fileNameRange(property, actualFileName);
		case MissingChecksum ignored -> List.of(fallback);
		};

		return ranges.isEmpty() ? List.of(fallback) : ranges;
	}

	private static List<TextRange> credentialsRange(PropertyImpl property) {

		String decoded = property.getUnescapedValue();
		if (decoded == null) {
			return List.of();
		}

		int authorityStart = MavenWrapperUrlRewriter.authorityStart(decoded);
		if (authorityStart < 0) {
			return List.of();
		}

		int authorityEnd = MavenWrapperUrlRewriter.authorityEnd(decoded, authorityStart);
		int at = decoded.indexOf('@', authorityStart);
		if (at < 0 || at >= authorityEnd) {
			return List.of();
		}

		return PropertyUtils.mapDecodedRanges(property, List.of(TextRange.create(authorityStart, at + 1)));
	}

	private static List<TextRange> coordinateRanges(PropertyImpl property, String... groupNames) {

		Matcher matcher = matcher(property);
		if (matcher == null) {
			return List.of();
		}

		List<TextRange> decodedRanges = new ArrayList<>();
		for (String groupName : groupNames) {
			if (matcher.start(groupName) >= 0) {
				decodedRanges.add(TextRange.create(matcher.start(groupName), matcher.end(groupName)));
			}
		}
		return PropertyUtils.mapDecodedRanges(property, decodedRanges);
	}

	private static List<TextRange> improperGroupRange(PropertyImpl property, String actualGroupPath) {

		Matcher matcher = matcher(property);
		if (matcher == null) {
			return List.of();
		}

		String groupId = matcher.group("groupId");
		int relativeStart = groupId.lastIndexOf(actualGroupPath);
		if (relativeStart < 0) {
			return List.of();
		}

		int start = matcher.start("groupId") + relativeStart;
		return PropertyUtils.mapDecodedRanges(property,
				List.of(TextRange.create(start, start + actualGroupPath.length())));
	}

	private static List<TextRange> unknownArtifactRanges(PropertyImpl property, String actualArtifactId) {

		Matcher matcher = matcher(property);
		if (matcher == null) {
			return List.of();
		}

		List<TextRange> decodedRanges = new ArrayList<>();
		addGroupRangeIfMatches(matcher, decodedRanges, "artifactId1", actualArtifactId);
		addGroupRangeIfMatches(matcher, decodedRanges, "artifactId2", actualArtifactId);
		return PropertyUtils.mapDecodedRanges(property, decodedRanges);
	}

	private static void addGroupRangeIfMatches(Matcher matcher, List<TextRange> ranges, String groupName,
			String expected) {

		if (expected.equals(matcher.group(groupName))) {
			ranges.add(TextRange.create(matcher.start(groupName), matcher.end(groupName)));
		}
	}

	private static List<TextRange> fileNameRange(PropertyImpl property, String actualFileName) {

		String decoded = property.getUnescapedValue();
		if (decoded == null) {
			return List.of();
		}

		int start = decoded.lastIndexOf(actualFileName);
		if (start < 0) {
			return List.of();
		}

		return PropertyUtils.mapDecodedRanges(property,
				List.of(TextRange.create(start, start + actualFileName.length())));
	}

	private static @Nullable Matcher matcher(PropertyImpl property) {

		String decoded = property.getUnescapedValue();
		if (decoded == null) {
			return null;
		}

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(decoded);
		return matcher.find() ? matcher : null;
	}

	private static @Nullable String resolveLatestNonPreviewVersion(StateService stateService,
			WrapperProperty property) {

		Release release = stateService.getCache().getReleases(property.artifactId()).stream()
				.filter(Predicate.not(Release::isPreview)).max(Comparator.naturalOrder()).orElse(null);
		return release != null ? release.getVersion().toString() : null;
	}

}
