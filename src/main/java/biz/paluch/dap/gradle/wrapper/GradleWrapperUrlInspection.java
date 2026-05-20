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

package biz.paluch.dap.gradle.wrapper;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.CredentialsInUrl;
import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.InvalidUrl;
import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.MalformedFileName;
import biz.paluch.dap.gradle.wrapper.GradleWrapperUrlProblem.UnknownArtifact;
import biz.paluch.dap.state.StateService;
import biz.paluch.dap.util.PropertyUtils;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInspection.LocalInspectionTool;
import com.intellij.codeInspection.ProblemsHolder;
import com.intellij.codeInspection.ProblemsHolder.ProblemBuilder;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiElementVisitor;

/**
 * Inspection for Gradle wrapper distribution URL issues.
 *
 * @author Mark Paluch
 */
public class GradleWrapperUrlInspection extends LocalInspectionTool implements DumbAware {

	private static final Pattern VERSION_IN_FILE_NAME = Pattern
			.compile("[\\w.-]+-(?<version>\\d[\\w.-]*?)(?:-(?:bin|all))?\\.zip(?=$|[?#])");

	@Override
	public PsiElementVisitor buildVisitor(ProblemsHolder holder, boolean isOnTheFly) {

		if (!GradleWrapperUtils.isWrapperFile(holder.getFile())) {
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

				List<GradleWrapperUrlProblem> problems = GradleWrapperUrlAnalyzer.analyze(decodedValue,
						property.getText());
				if (problems.isEmpty()) {
					return;
				}

				String latestVersion = latestNonPreviewVersion(stateService);
				String version = resolveVersion(property, latestVersion);

				for (GradleWrapperUrlProblem problem : problems) {
					registerProblem(holder, property, rangeInElement, problem, version, latestVersion);
				}
			}

		};
	}

	private static void registerProblem(ProblemsHolder holder, PropertyImpl property, TextRange rangeInElement,
			GradleWrapperUrlProblem problem, String version, String latestVersion) {

		for (TextRange range : rangesFor(property, rangeInElement, problem)) {

			ProblemBuilder builder = holder.problem(property, problem.getMessage()).range(range);
			problem.getFixes(version).forEach(builder::fix);
			builder.fix(GradleWrapperUrlFixes.useDefaultUrl(latestVersion));
			builder.register();
		}
	}

	private static List<TextRange> rangesFor(PropertyImpl property, TextRange fallback,
			GradleWrapperUrlProblem problem) {

		List<TextRange> ranges = switch (problem) {
		case CredentialsInUrl ignored -> credentialsRange(property);
		case InvalidUrl ignored -> List.of();
		case UnknownArtifact(String actualArtifactId) -> artifactRange(property, actualArtifactId);
		case MalformedFileName(String actualFileName) -> fileNameRange(property, actualFileName);
		};

		return ranges.isEmpty() ? List.of(fallback) : ranges;
	}

	private static List<TextRange> credentialsRange(PropertyImpl property) {

		String decoded = property.getUnescapedValue();
		if (decoded == null) {
			return List.of();
		}

		int authorityStart = GradleWrapperUrlRewriter.authorityStart(decoded);
		if (authorityStart < 0) {
			return List.of();
		}

		int authorityEnd = GradleWrapperUrlRewriter.authorityEnd(decoded, authorityStart);
		int at = decoded.indexOf('@', authorityStart);
		if (at < 0 || at >= authorityEnd) {
			return List.of();
		}

		return PropertyUtils.mapDecodedRanges(property, List.of(TextRange.create(authorityStart, at + 1)));
	}

	private static List<TextRange> artifactRange(PropertyImpl property, String actualArtifactId) {

		String decoded = property.getUnescapedValue();
		if (decoded == null) {
			return List.of();
		}

		String fileName = GradleWrapperUrlRewriter.lastUrlSegment(decoded);
		int artifactStartInFile = fileName.indexOf(actualArtifactId);
		int fileStart = decoded.lastIndexOf(fileName);
		if (fileStart < 0 || artifactStartInFile < 0) {
			return List.of();
		}

		int start = fileStart + artifactStartInFile;
		return PropertyUtils.mapDecodedRanges(property,
				List.of(TextRange.create(start, start + actualArtifactId.length())));
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

	private static String latestNonPreviewVersion(StateService stateService) {
		return WrapperProperty.DISTRIBUTION.getLatestRelease(stateService.getCache()).getVersion().toString();
	}

	private static String resolveVersion(PropertyImpl property, String latestVersion) {

		GradleWrapperEntry entry = GradleWrapperParser.parse(property);
		if (entry != null && StringUtils.hasText(entry.versionText())) {
			return entry.versionText();
		}
		String decoded = property.getUnescapedValue();
		if (decoded != null) {
			Matcher matcher = VERSION_IN_FILE_NAME.matcher(GradleWrapperUrlRewriter.lastUrlSegment(decoded));
			if (matcher.find()) {
				return matcher.group("version");
			}
		}
		return latestVersion;
	}

}
