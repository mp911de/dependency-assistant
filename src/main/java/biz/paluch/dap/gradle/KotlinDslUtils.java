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
package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.ArtifactId;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.jetbrains.kotlin.psi.*;
import org.jspecify.annotations.Nullable;

import org.springframework.util.StringUtils;

import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.impl.source.tree.LeafPsiElement;
import com.intellij.psi.util.PsiTreeUtil;

/**
 * PSI navigation utilities for KotlinScript Gradle build files.
 *
 * @author Mark Paluch
 */
class KotlinDslUtils {

	private static final Pattern GRADLE_DEPENDENCY_VERSION_PATTERN = Pattern
			.compile("([\\w.]+):(\\w[\\w.-]*):(.+)(:(.+))?");

	private KotlinDslUtils() {}

	// -------------------------------------------------------------------------
	// Version element location
	// -------------------------------------------------------------------------

	static void doWithStrings(KtStringTemplateExpression element, Consumer<String> text,
			Consumer<KtExpression> expressionConsumer) {

		for (PsiElement child : element.getChildren()) {
			if (child instanceof KtStringTemplateExpression kse) {
				doWithStrings(kse, text, expressionConsumer);
			} else if (child instanceof KtBlockStringTemplateEntry block) {
				for (KtExpression expression : block.getExpressions()) {
					expressionConsumer.accept(expression);
				}
			} else {
				text.accept(child.getText());
			}
		}
	}

	static void doWithStrings(KtElement element, Consumer<String> text, Consumer<KtExpression> expressionConsumer) {

		for (PsiElement child : element.getChildren()) {
			if (child instanceof KtStringTemplateExpression kse) {
				doWithStrings(kse, text, expressionConsumer);
			} else if (child instanceof KtLiteralStringTemplateEntry kse) {
				text.accept(kse.getText());
			} else if (child instanceof KtElement kte) {
				doWithStrings(kte, text, expressionConsumer);
			}
		}
	}

	/**
	 * Returns the version segment {@link KtCallElement} if the caret is inside the version part of a Kotlin DSL
	 * string-notation dependency ({@code "group:artifact:version"}), or {@code null}.
	 */
	public static GroovyDslUtils.@Nullable VersionLocation findKotlinVersionElement(KtCallElement call) {

		PsiFile file = call.getContainingFile();
		if (!GradleUtils.isGradleFile(file)) {
			return null;
		}

		KtBinaryExpression be = PsiTreeUtil.getParentOfType(call, KtBinaryExpression.class);
		KtCallExpression parentCall = PsiTreeUtil.getParentOfType(call, KtCallExpression.class);
		if (parentCall == null) {
			return null;
		}

		String methodName = getKotlinCallName(call);
		if (!StringUtils.hasText(methodName) || !isDependencyCall(call)) {
			return null;
		}
		StringBuilder raw = new StringBuilder();
		StringBuilder rawVersion = new StringBuilder();
		StringBuilder property = new StringBuilder();

		doWithStrings(call, text -> {
			raw.append(text);
		}, ktExpression -> {

			if (ktExpression instanceof KtCallExpression ktCall) {
				String name = getKotlinCallName(ktCall);
				if ("property".equals(name)) {
					doWithStrings(ktExpression, property::append, e -> {});
				}
			}
		});

		if (be != null) {
			PsiElement[] children = be.getChildren();

			for (int i = 0; i < children.length; i++) {
				PsiElement child = children[i];
				if (child instanceof KtOperationReferenceExpression ops) {

					if (ops.getReferencedName().equals("version") && children.length > i + 1
							&& children[i + 1] instanceof KtStringTemplateExpression versionExpr) {

						for (KtStringTemplateEntry entry : versionExpr.getEntries()) {
							rawVersion.append(entry.getText());
						}
					}
				}
			}
		}

		return getVersionLocation(call, parentCall, raw.toString(), rawVersion.toString(), property.toString());
	}

	private static GroovyDslUtils.@Nullable VersionLocation getVersionLocation(KtCallElement call,
			KtCallExpression parentCall, String raw, String rawVersion, String property) {

		boolean isProperty = StringUtils.hasText(property);
		if (isProperty) {
			rawVersion = property;
		}

		String kotlinCallName = getKotlinCallName(parentCall);
		if ("plugins".equals(kotlinCallName)) {
			return new GroovyDslUtils.VersionLocation(call, ArtifactId.of(raw, raw), rawVersion, isProperty);
		}

		// Infix plugin form: id("plugin.id") version "x.y.z" — parentCall may not be `plugins` depending on PSI shape.
		if ("id".equals(getKotlinCallName(call)) && isInsideKotlinBlock(call, "plugins") && !raw.contains(":")) {
			return new GroovyDslUtils.VersionLocation(call, ArtifactId.of(raw.trim(), raw.trim()), rawVersion, isProperty);
		}

		String[] parts = raw.split(":");
		if (parts.length < 2) {
			return null;
		}

		ArtifactId artifactId = ArtifactId.of(parts[0].trim(), parts[1].trim());

		if (StringUtils.hasText(rawVersion)) {
			return new GroovyDslUtils.VersionLocation(call, ArtifactId.of(parts[0].trim(), parts[1].trim()),
					rawVersion.toString(), isProperty);
		}

		if (parts.length < 3) {
			return null;
		}

		return new GroovyDslUtils.VersionLocation(call, artifactId, parts[2].trim(), false);
	}

	/**
	 * Returns the version segment {@link KtCallElement} if the caret is inside the version part of a Kotlin DSL
	 * string-notation dependency ({@code "group:artifact:version"}), or {@code null}.
	 */
	public static GroovyDslUtils.@Nullable VersionLocation findKotlinVersionElement(KtCallElement call,
			KtStringTemplateEntry stringTemplate) {

		KtCallExpression parentCall = PsiTreeUtil.getParentOfType(call, KtCallExpression.class);
		if (parentCall == null) {
			return null;
		}

		String methodName = getKotlinCallName(call);
		if (!StringUtils.hasText(methodName) || !isDependencyCall(call)) {
			return null;
		}
		StringBuilder raw = new StringBuilder();
		StringBuilder property = new StringBuilder();
		StringBuilder rawVersion = new StringBuilder();

		String templateText = stringTemplate.getText();
		Matcher matcher = GRADLE_DEPENDENCY_VERSION_PATTERN.matcher(templateText);
		if (matcher.find()) {

			String groupId = matcher.group(1);
			String artifactId = matcher.group(2);
			String version = matcher.group(3);

			return new GroovyDslUtils.VersionLocation(call, ArtifactId.of(groupId, artifactId), version, false);
		}

		doWithStrings(call, text -> {
			raw.append(text);
		}, ktExpression -> {

			if (ktExpression instanceof KtCallExpression ktCall) {
				String name = getKotlinCallName(ktCall);
				if ("property".equals(name)) {
					doWithStrings(ktExpression, property::append, e -> {});
				}
			}
		});

		if (!StringUtils.hasText(rawVersion) && !StringUtils.hasText(property)) {
			rawVersion.append(stringTemplate.getText());
		}

		return getVersionLocation(call, parentCall, raw.toString(), rawVersion.toString(), property.toString());
	}

	/**
	 * Returns the property name.
	 */
	public static @Nullable String findProperty(KtBinaryExpression element) {

		for (PsiElement child : element.getChildren()) {

			if (child instanceof KtArrayAccessExpression array) {
				KtExpression arrayExpression = array.getArrayExpression();
				List<KtExpression> indexExpressions = array.getIndexExpressions();

				if (arrayExpression == null || !"extra".equals(arrayExpression.getText()) || indexExpressions.size() != 1) {
					return null;
				}

				StringBuilder builder = new StringBuilder();
				KtExpression indexExpression = indexExpressions.getFirst();
				doWithStrings(indexExpression, builder::append, e -> {});

				return builder.toString();
			}
		}

		return null;
	}

	/**
	 * Returns the version segment {@link PsiElement} if the caret is inside the version part of a Kotlin DSL
	 * string-notation dependency ({@code "group:artifact:version"}), or {@code null}.
	 */
	public static GroovyDslUtils.@Nullable VersionLocation findKotlinVersionElement(PsiElement element) {

		PsiFile file = element.getContainingFile();
		if (!GradleUtils.isGradleFile(file)) {
			return null;
		}

		KtStringTemplateExpression strTemplate = PsiTreeUtil.getParentOfType(element, KtStringTemplateExpression.class);
		if (strTemplate == null) {
			return null;
		}

		KtCallExpression call = PsiTreeUtil.getParentOfType(strTemplate, KtCallExpression.class);
		if (call == null) {
			return null;
		}

		String methodName = getKotlinCallName(call);
		if (!GradleUtils.DEPENDENCY_CONFIGS.contains(methodName)) {
			return null;
		}

		PsiElement[] children = strTemplate.getChildren();
		String raw;
		if (children.length > 1 && children[1] instanceof KtStringTemplateExpression str) {
			raw = str.getText();

		} else {
			raw = strTemplate.getText();
			// Strip surrounding quotes
			if (raw.startsWith("\"") && raw.endsWith("\"")) {
				raw = raw.substring(1, raw.length() - 1);
			}
		}

		String[] parts = raw.split(":");
		if (parts.length < 3) {
			return null;
		}

		return new GroovyDslUtils.VersionLocation(strTemplate, ArtifactId.of(parts[0].trim(), parts[1].trim()),
				parts[2].trim(), false);
	}

	// -------------------------------------------------------------------------
	// Helpers
	// -------------------------------------------------------------------------

	static boolean isDependencyCall(KtCallElement call) {

		String methodName = getKotlinCallName(call);
		if (!StringUtils.hasText(methodName)) {
			return false;
		}

		if (GradleUtils.DEPENDENCY_CONFIGS.contains(methodName) || GradleUtils.PLATFORM_FUNCTIONS.contains(methodName)) {
			return true;
		}
		return "id".equals(methodName) && isInsideKotlinBlock(call, "plugins");
	}

	/**
	 * Find a {@link KtCallExpression} that is a dependency declaration.
	 *
	 * @param version version element that defines the dependency version.
	 * @return
	 */
	static @Nullable KtCallExpression findDependencyExpression(PsiElement version) {

		KtBinaryExpression binary = PsiTreeUtil.getParentOfType(version, KtBinaryExpression.class);
		KtCallExpression call = PsiTreeUtil.getParentOfType(version, KtCallExpression.class);
		if (binary == null && call != null && isDependencyCall(call)) {

			if (version.getNextSibling() instanceof KtBlockStringTemplateEntry entry) {
				return null;
			}

			return call;
		}

		if (binary != null && call != null && isDependencyCall(call)) {
			return null;
		}

		if (binary != null && binary != version) {
			PsiElement previous = version.getPrevSibling();
			while (previous != null && !(previous instanceof PsiFile)) {

				if (previous instanceof KtOperationReferenceExpression ops) {
					if ("version".equals(ops.getReferencedName())) {
						for (PsiElement child : binary.getChildren()) {
							if (child instanceof KtCallExpression nested && isDependencyCall(nested)) {
								return nested;
							}
						}
					}
				}

				if (previous.getPrevSibling() != null) {
					previous = previous.getPrevSibling();
				} else {
					previous = previous.getParent();
				}
			}
		}

		return null;
	}

	/**
	 * Find a {@link KtBinaryExpression} {@code extra["key"] = rhs} whose value is defined by the given PSI (direct string
	 * RHS, inside {@code buildString { append("…") }}, triple-quoted literal, or {@code "….also { … = it }}).
	 *
	 * @param version a leaf or entry inside the version literal, or (for {@code it}-assignment) inside the receiver
	 *          string of {@code .also}
	 */
	static @Nullable KtBinaryExpression findPropertyExpression(PsiElement version) {

		KtBinaryExpression binary = PsiTreeUtil.getParentOfType(version, KtBinaryExpression.class);
		while (binary != null) {
			if (!"=".equals(binary.getOperationReference().getText())) {
				binary = PsiTreeUtil.getParentOfType(binary, KtBinaryExpression.class);
				continue;
			}
			KtExpression left = binary.getLeft();
			if (!(left instanceof KtArrayAccessExpression arrayAccess)) {
				binary = PsiTreeUtil.getParentOfType(binary, KtBinaryExpression.class);
				continue;
			}
			KtExpression receiver = arrayAccess.getArrayExpression();
			if (!(receiver instanceof KtNameReferenceExpression nameRef) || !"extra".equals(nameRef.getReferencedName())) {
				binary = PsiTreeUtil.getParentOfType(binary, KtBinaryExpression.class);
				continue;
			}
			KtExpression right = binary.getRight();
			if (right == null) {
				binary = PsiTreeUtil.getParentOfType(binary, KtBinaryExpression.class);
				continue;
			}
			if (PsiTreeUtil.isAncestor(right, version, false)) {
				return binary;
			}
			binary = PsiTreeUtil.getParentOfType(binary, KtBinaryExpression.class);
		}
		// "v".also { extra["k"] = it } — version PSI lives in the receiver, not under the assignment RHS.
		return findExtraItAssignmentFromAlsoReceiver(version);
	}

	private static @Nullable KtBinaryExpression findExtraItAssignmentFromAlsoReceiver(PsiElement version) {

		KtQualifiedExpression qual = PsiTreeUtil.getParentOfType(version, KtQualifiedExpression.class);
		while (qual != null) {
			KtExpression recv = qual.getReceiverExpression();
			if (recv != null && PsiTreeUtil.isAncestor(recv, version, false)) {
				KtExpression selector = qual.getSelectorExpression();
				if (selector instanceof KtCallExpression alsoCall && "also".equals(getKotlinCallName(alsoCall))) {
					KtLambdaExpression lambda = firstLambdaArgument(alsoCall);
					if (lambda != null) {
						KtExpression body = lambda.getBodyExpression();
						if (body != null) {
							for (KtBinaryExpression assign : PsiTreeUtil.collectElementsOfType(body, KtBinaryExpression.class)) {
								if (!"=".equals(assign.getOperationReference().getText())) {
									continue;
								}
								if (!(assign.getLeft() instanceof KtArrayAccessExpression arrayAccess)) {
									continue;
								}
								KtExpression arrayExpr = arrayAccess.getArrayExpression();
								if (!(arrayExpr instanceof KtNameReferenceExpression nr) || !"extra".equals(nr.getReferencedName())) {
									continue;
								}
								if (assign.getRight() instanceof KtNameReferenceExpression ref
										&& "it".equals(ref.getReferencedName())) {
									return assign;
								}
							}
						}
					}
				}
				return null;
			}
			qual = PsiTreeUtil.getParentOfType(qual, KtQualifiedExpression.class);
		}
		return null;
	}

	private static @Nullable KtLambdaExpression firstLambdaArgument(KtCallExpression call) {

		for (ValueArgument va : call.getValueArguments()) {
			KtExpression expr = va.getArgumentExpression();
			if (expr instanceof KtLambdaExpression lam) {
				return lam;
			}
		}
		return null;
	}

	static @Nullable String getKotlinCallName(KtCallExpression call) {
		PsiElement callee = call.getCalleeExpression();
		if (callee instanceof KtNameReferenceExpression ref) {
			return ref.getReferencedName();
		}
		return callee != null ? callee.getText() : null;
	}

	static @Nullable String getKotlinCallName(KtCallElement call) {
		PsiElement callee = call.getCalleeExpression();
		if (callee instanceof KtNameReferenceExpression ref) {
			return ref.getReferencedName();
		}
		return callee != null ? callee.getText() : null;
	}

	static boolean isInsideKotlinBlock(PsiElement element, String blockName) {
		PsiElement parent = element.getParent();
		while (parent != null) {
			if (parent instanceof KtCallExpression parentCall) {
				String name = getKotlinCallName(parentCall);
				if (blockName.equals(name)) {
					return true;
				}
			}
			parent = parent.getParent();
		}
		return false;
	}

	// -------------------------------------------------------------------------
	// Version catalog (Kotlin {@code libs.…})
	// -------------------------------------------------------------------------

	/**
	 * Innermost {@code alias}/{@code id}/{@code implementation}/… call whose first argument is a {@code libs.…} chain and
	 * that contains {@code element}.
	 */
	static @Nullable KtCallExpression findEnclosingCatalogAccessorCall(PsiElement element) {

		for (PsiElement p = element; p != null; p = p.getParent()) {
			if (!(p instanceof KtCallExpression call)) {
				continue;
			}
			if (!isKotlinCatalogConsumerCall(call)) {
				continue;
			}
			KtExpression arg = getFirstCatalogValueArgument(call);
			if (arg == null || !isKotlinLibsCatalogRootExpression(arg)) {
				continue;
			}
			if (!PsiTreeUtil.isAncestor(arg, element, false)) {
				continue;
			}
			return call;
		}
		return null;
	}

	private static boolean isKotlinCatalogConsumerCall(KtCallExpression call) {

		String name = getKotlinCallName(call);
		if (!StringUtils.hasText(name)) {
			return false;
		}
		if ("alias".equals(name)) {
			return true;
		}
		if ("id".equals(name) && isInsideKotlinBlock(call, "plugins")) {
			return true;
		}
		if (GradleUtils.DEPENDENCY_CONFIGS.contains(name) || GradleUtils.PLATFORM_FUNCTIONS.contains(name)) {
			return true;
		}
		return false;
	}

	static @Nullable KtExpression getFirstCatalogValueArgument(KtCallExpression call) {

		for (ValueArgument va : call.getValueArguments()) {
			return va.getArgumentExpression();
		}
		return null;
	}

	static boolean isKotlinLibsCatalogRootExpression(KtExpression expr) {

		List<String> segs = collectKotlinCatalogDotSegments(expr);
		return segs != null && !segs.isEmpty() && "libs".equals(segs.get(0));
	}

	static @Nullable List<String> collectKotlinCatalogDotSegments(KtExpression expr) {

		ArrayList<String> reversed = new ArrayList<>();
		KtExpression cur = expr;
		while (cur instanceof KtDotQualifiedExpression dq) {
			String seg = kotlinSelectorToSegment(dq.getSelectorExpression());
			if (seg == null) {
				return null;
			}
			reversed.add(seg);
			cur = dq.getReceiverExpression();
		}
		if (cur instanceof KtNameReferenceExpression ref) {
			reversed.add(ref.getReferencedName());
		} else {
			return null;
		}
		Collections.reverse(reversed);
		return reversed;
	}

	private static @Nullable String kotlinSelectorToSegment(KtExpression selector) {

		if (selector instanceof KtNameReferenceExpression ref) {
			return ref.getReferencedName();
		}
		if (selector instanceof KtCallExpression call && call.getValueArguments().isEmpty()) {
			return getKotlinCallName(call);
		}
		return null;
	}

	/**
	 * {@code true} for PSI nodes that duplicate the same version hit as {@link KtLiteralStringTemplateEntry} (leaf tokens
	 * under that entry, or the wrapping {@link KtStringTemplateExpression} when it has no {@code ${…}} segments).
	 * <p>
	 * Matches the Groovy rule that only
	 * {@link org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral} is a version anchor,
	 * not each leaf under it.
	 */
	static boolean isRedundantKotlinVersionHighlightAnchor(PsiElement element) {

		if (element instanceof LeafPsiElement && element.getParent() instanceof KtLiteralStringTemplateEntry) {
			return true;
		}
		if (element instanceof KtStringTemplateExpression st) {
			KtStringTemplateEntry[] entries = st.getEntries();
			if (entries.length == 0) {
				return false;
			}
			for (KtStringTemplateEntry e : entries) {
				if (e instanceof KtBlockStringTemplateEntry) {
					return false;
				}
			}
			return true;
		}
		KtCallExpression catalogCall = findEnclosingCatalogAccessorCall(element);
		if (catalogCall != null) {
			KtExpression firstArg = getFirstCatalogValueArgument(catalogCall);
			if (firstArg != null && PsiTreeUtil.isAncestor(firstArg, element, false) && !firstArg.equals(element)) {
				return true;
			}
		}
		return false;
	}

}
