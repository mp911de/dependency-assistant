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

package biz.paluch.dap.gradle;

import java.util.HashSet;
import java.util.Set;

import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.gradle.GroovyDslParser.GroovyDeclarationCall;
import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.PsiFileCache;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.arguments.GrNamedArgument;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.blocks.GrClosableBlock;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrReferenceExpression;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.literals.GrLiteral;
import org.jspecify.annotations.Nullable;

/**
 * Groovy DSL declaration-style detection.
 *
 * <p>A version element is a literal or a reference. It is classified by the
 * call that owns it: a named {@code version:} argument, a constraint call
 * inside a {@code version { ... }} block, the chained {@code version} call of a
 * plugin declaration, or the coordinate of a dependency call. A literal that
 * declares a property referenced by one of those positions is a backing
 * property.
 *
 * @author Mark Paluch
 * @see DeclarationStyleDetector
 */
class GroovyDeclarationStyleDetector implements DeclarationStyleDetector {

	private static final GroovyDeclarationStyleDetector INSTANCE = new GroovyDeclarationStyleDetector();

	public static GroovyDeclarationStyleDetector getInstance() {
		return INSTANCE;
	}

	@Override
	public DeclarationStyle detect(PsiElement element) {

		GrLiteral literal = PsiTreeUtil.getParentOfType(element, GrLiteral.class, false);
		if (literal != null) {

			DeclarationStyle style = classify(literal);
			if (style != null) {
				return style;
			}
		}

		GrReferenceExpression commandPlatformString = GroovyDslUtils.findCommandPlatformString(element);
		if (commandPlatformString != null) {
			return DeclarationStyle.commandPlatform(commandPlatformString,
					GroovyDslUtils.getCommandPlatformCall(commandPlatformString));
		}

		GrReferenceExpression reference = PsiTreeUtil.getParentOfType(element, GrReferenceExpression.class, false);
		if (reference != null) {

			DeclarationStyle style = classify(reference);
			if (style != null) {
				return style;
			}
		}

		return DeclarationStyle.absent();
	}

	/**
	 * Return the quoted coordinate of the command-style platform declaration at or
	 * enclosing the given element.
	 * @see GroovyDslUtils#findCommandPlatformString(PsiElement)
	 */
	@Nullable
	PsiElement findCommandPlatformString(PsiElement element) {
		return GroovyDslUtils.findCommandPlatformString(element);
	}

	/**
	 * Classify a version literal or reference by the call that owns it.
	 */
	private static @Nullable DeclarationStyle classify(PsiElement element) {

		// version: '1.0' or version: springVersion
		GrNamedArgument namedArgument = PsiTreeUtil.getParentOfType(element, GrNamedArgument.class);
		if (namedArgument != null && GradleUtils.VERSION.equals(namedArgument.getLabelName())
				&& isExpressionOf(namedArgument, element)) {

			GrMethodCall owner = PsiTreeUtil.getParentOfType(namedArgument, GrMethodCall.class);
			if (isDependencyCall(owner)) {
				return DeclarationStyle.mapNotation(element, owner);
			}
		}

		GrMethodCall call = PsiTreeUtil.getParentOfType(element, GrMethodCall.class);
		if (call != null && isArgumentOfCall(element, call)) {

			// prefer '1.0', strictly springVersion, require '[1.0,2.0)'
			String callName = GroovyDslUtils.getGroovyMethodName(call);
			if (GradleVersionConstraint.isConstraint(callName)) {

				GrMethodCall owner = findVersionBlockDependencyCall(call);
				return owner != null ? DeclarationStyle.versionBlock(callName, element, owner) : null;
			}

			if (element instanceof GrLiteral) {

				// id 'x' version '1.0'
				GrMethodCall idCall = findPluginIdCallForVersionCall(call);
				if (idCall != null) {
					return DeclarationStyle.pluginVersion(element, idCall);
				}

				// implementation 'g:a:1.0'
				if (!(element.getParent() instanceof GrNamedArgument) && isDependencyCall(call)
						&& call.getClosureArguments().length == 0) {
					return DeclarationStyle.inline(element, call);
				}
			}
		}

		return element instanceof GrLiteral literal && isBackingVersionProperty(literal)
				? DeclarationStyle.backingProperty(literal)
				: null;
	}

	/**
	 * Return whether the literal declares an {@code ext} or script property that a
	 * dependency or plugin declaration in the same file references as version.
	 */
	private static boolean isBackingVersionProperty(GrLiteral literal) {

		GroovyExtAssignment assignment = GroovyExtAssignment.from(literal);
		if (assignment == null) {
			return false;
		}

		PsiFile file = literal.getContainingFile();
		return file != null && referencedVersionProperties(file).contains(assignment.getKey());
	}

	/**
	 * Property names that declarations in the file reference as version. Derived
	 * from the forward parse and cached until the file changes.
	 */
	private static Set<String> referencedVersionProperties(PsiFile file) {
		return PsiFileCache.get(file, GroovyDeclarationStyleDetector::computeReferencedVersionProperties);
	}

	private static Set<String> computeReferencedVersionProperties(PsiFile file) {

		Set<String> names = new HashSet<>();
		GroovyDslFileParser parser = new GroovyDslFileParser(file, PropertyResolver.empty());

		for (ArtifactDeclaration declaration : parser.parseDeclarations()) {
			if (declaration.getVersionSource() instanceof VersionSource.VersionProperty property) {
				names.add(property.getProperty());
			}
		}

		return names;
	}

	/**
	 * Walk up from a constraint call to the enclosing dependency call, returning it
	 * when the full {@code dependency { version { constraint } }} structure is
	 * present.
	 */
	private static @Nullable GrMethodCall findVersionBlockDependencyCall(GrMethodCall constraintCall) {

		GrClosableBlock versionClosure = PsiTreeUtil.getParentOfType(constraintCall, GrClosableBlock.class);
		GrMethodCall versionCall = versionClosure != null
				? PsiTreeUtil.getParentOfType(versionClosure, GrMethodCall.class)
				: null;
		if (versionCall == null || !GradleUtils.VERSION.equals(GroovyDslUtils.getGroovyMethodName(versionCall))) {
			return null;
		}

		GrClosableBlock dependencyClosure = PsiTreeUtil.getParentOfType(versionCall, GrClosableBlock.class);
		GrMethodCall dependencyCall = dependencyClosure != null
				? PsiTreeUtil.getParentOfType(dependencyClosure, GrMethodCall.class)
				: null;
		return isDependencyCall(dependencyCall) ? dependencyCall : null;
	}

	/**
	 * Return the plugin {@code id(...)} call if {@code call} is the chained
	 * {@code version(...)} call in a Groovy plugin declaration.
	 */
	private static @Nullable GrMethodCall findPluginIdCallForVersionCall(GrMethodCall call) {

		if (!(call.getInvokedExpression() instanceof GrReferenceExpression versionReference)
				|| !GradleUtils.VERSION.equals(versionReference.getReferenceName())
				|| !(versionReference.getQualifierExpression() instanceof GrMethodCall idCall)) {
			return null;
		}

		GroovyDeclarationCall declaration = GroovyDeclarationCall.from(idCall);
		return declaration != null && declaration.isPlugin() ? idCall : null;
	}

	private static boolean isDependencyCall(@Nullable GrMethodCall call) {
		GroovyDeclarationCall declaration = GroovyDeclarationCall.from(call);
		return declaration != null && declaration.isDependency();
	}

	private static boolean isExpressionOf(GrNamedArgument namedArgument, PsiElement element) {
		PsiElement expression = namedArgument.getExpression();
		return expression != null && PsiTreeUtil.isAncestor(expression, element, false);
	}

	private static boolean isArgumentOfCall(PsiElement element, GrMethodCall call) {

		for (PsiElement argument : call.getArgumentList().getAllArguments()) {
			if (PsiTreeUtil.isAncestor(argument, element, false)) {
				return true;
			}
		}

		return false;
	}

}
