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

import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * The role a PSI element plays in a Gradle dependency or plugin version
 * declaration, together with the version element it carries and, when the
 * version is declared inside a call, the call that owns it.
 *
 * <p>Obtained from {@link DeclarationStyleDetector#detect(PsiElement)}, which
 * never returns {@literal null}: a non-version element yields
 * {@link #absent()}. Callers guard with {@link #isPresent()} or
 * {@link #isAbsent()} before reading {@link #kind()},
 * {@link #versionElement()}, or {@link #owningCall()}, which are available only
 * on a present style. It is a structural classification only; it does not
 * resolve or parse the declaration.
 *
 * <p>Use the kind-aligned factories ({@link #inline}, {@link #mapNotation},
 * {@link #versionBlock}, {@link #pluginVersion}, {@link #backingProperty},
 * {@link #commandPlatform}) to build a present position and {@link #absent()}
 * for the no-position marker.
 *
 * @author Mark Paluch
 * @see DeclarationStyleDetector
 */
interface DeclarationStyle {

	/**
	 * Return whether this is a present style, that is the element sits in a
	 * recognized version declaration.
	 * @return {@literal true} for a present style; {@literal false} for
	 * {@link #absent()}.
	 */
	boolean isPresent();

	/**
	 * Return whether this is the {@link #absent()} marker.
	 * @return {@literal true} for {@link #absent()}; {@literal false} otherwise.
	 */
	default boolean isAbsent() {
		return !isPresent();
	}

	/**
	 * Return the structural kind of this declaration style.
	 * @return the kind.
	 * @throws IllegalStateException if this style {@link #isAbsent()}.
	 */
	Kind kind();

	/**
	 * Return the element that carries the version value or reference.
	 * @return the version element.
	 * @throws IllegalStateException if this style {@link #isAbsent()}.
	 */
	PsiElement versionElement();

	/**
	 * Return the dependency or plugin call that owns the version when
	 * {@link Kind#isInlineInCall()}.
	 * @return the owning call, or {@literal null} for backing properties and when
	 * the owning call cannot be resolved.
	 * @throws IllegalStateException if this style {@link #isAbsent()}.
	 */
	@Nullable
	PsiElement owningCall();

	/**
	 * Return the marker for an element that is not part of a recognized version
	 * declaration.
	 * @return the shared absent marker.
	 */
	static DeclarationStyle absent() {
		return Absent.INSTANCE;
	}

	/**
	 * Create a {@link Kind#INLINE_NOTATION} style.
	 * @param versionElement the coordinate literal.
	 * @param owningCall the owning dependency or platform call; can be
	 * {@literal null}.
	 * @return the declaration style.
	 */
	static DeclarationStyle inline(PsiElement versionElement, @Nullable PsiElement owningCall) {
		return new Site(Kind.INLINE_NOTATION, versionElement, owningCall);
	}

	/**
	 * Create a {@link Kind#NAMED_ARGUMENT} (map-notation {@code version}) style.
	 * @param versionElement the version literal or reference.
	 * @param owningCall the owning dependency call; can be {@literal null}.
	 * @return the declaration style.
	 */
	static DeclarationStyle mapNotation(PsiElement versionElement, @Nullable PsiElement owningCall) {
		return new Site(Kind.NAMED_ARGUMENT, versionElement, owningCall);
	}

	/**
	 * Create a version-block constraint style.
	 * @param kind {@link Kind#VERSION_BLOCK_PREFER} or
	 * {@link Kind#VERSION_BLOCK_STRICTLY}.
	 * @param versionElement the constraint version literal or reference; must not
	 * be {@literal null}.
	 * @param owningCall the owning dependency call; can be {@literal null}.
	 * @return the declaration style.
	 * @throws IllegalArgumentException if {@code kind} is not a version-block kind.
	 */
	static DeclarationStyle versionBlock(Kind kind, PsiElement versionElement, @Nullable PsiElement owningCall) {
		if (kind != Kind.VERSION_BLOCK_PREFER && kind != Kind.VERSION_BLOCK_STRICTLY) {
			throw new IllegalArgumentException("Not a version-block kind: %s".formatted(kind));
		}
		return new Site(kind, versionElement, owningCall);
	}

	/**
	 * Create a {@link Kind#PLUGIN_VERSION} style.
	 * @param versionElement the plugin version literal; must not be
	 * {@literal null}.
	 * @param owningCall the owning plugin {@code id(...)} call; can be
	 * {@literal null}.
	 * @return the declaration style.
	 */
	static DeclarationStyle pluginVersion(PsiElement versionElement, @Nullable PsiElement owningCall) {
		return new Site(Kind.PLUGIN_VERSION, versionElement, owningCall);
	}

	/**
	 * Create a {@link Kind#BACKING_PROPERTY} style. Backing properties carry no
	 * owning call; they are resolved through the property declaration.
	 * @param versionElement the backing property value literal; must not be
	 * {@literal null}.
	 * @return the declaration style.
	 */
	static DeclarationStyle backingProperty(PsiElement versionElement) {
		return new Site(Kind.BACKING_PROPERTY, versionElement, null);
	}

	/**
	 * Create a {@link Kind#COMMAND_PLATFORM} style.
	 * @param versionElement the command-style platform string element; must not be
	 * {@literal null}.
	 * @param owningCall the owning dependency call; can be {@literal null}.
	 * @return the declaration style.
	 */
	static DeclarationStyle commandPlatform(PsiElement versionElement, @Nullable PsiElement owningCall) {
		return new Site(Kind.COMMAND_PLATFORM, versionElement, owningCall);
	}

	/**
	 * Structural kind of a version declaration in a Gradle build script.
	 */
	enum Kind {

		/**
		 * Compact coordinate notation, e.g. {@code implementation("g:a:1.0")}.
		 */
		INLINE_NOTATION(true),

		/**
		 * Map-style {@code version} argument, e.g. {@code version = "1.0"}.
		 */
		NAMED_ARGUMENT(true),

		/**
		 * {@code version { prefer(...) }} constraint.
		 */
		VERSION_BLOCK_PREFER(true),

		/**
		 * {@code version { strictly(...) }} constraint.
		 */
		VERSION_BLOCK_STRICTLY(true),

		/**
		 * Plugin DSL {@code version} operand, e.g. {@code id("x") version "1.0"}.
		 */
		PLUGIN_VERSION(true),

		/**
		 * A local/ext property literal that backs a supported version reference.
		 */
		BACKING_PROPERTY(false),

		/**
		 * Groovy command-style platform string, e.g.
		 * {@code implementation platform "g:a:1.0"}.
		 */
		COMMAND_PLATFORM(false);

		private final boolean inlineInCall;

		Kind(boolean inlineInCall) {
			this.inlineInCall = inlineInCall;
		}

		/**
		 * Return whether the version is declared inside a dependency or plugin call and
		 * is resolved by re-parsing {@link DeclarationStyle#owningCall()}, as opposed
		 * to a backing property or command-style platform string that callers resolve
		 * through a dedicated path.
		 * @return {@literal true} for call-inline positions; {@literal false}
		 * otherwise.
		 */
		boolean isInlineInCall() {
			return inlineInCall;
		}

	}

	/**
	 * A present {@link DeclarationStyle}: a recognized version declaration.
	 */
	record Site(Kind kind, PsiElement versionElement, @Nullable PsiElement owningCall) implements DeclarationStyle {

		@Override
		public boolean isPresent() {
			return true;
		}

	}

	/**
	 * The marker returned when an element is not part of a recognized version
	 * declaration.
	 */
	enum Absent implements DeclarationStyle {

		INSTANCE;

		@Override
		public boolean isPresent() {
			return false;
		}

		@Override
		public Kind kind() {
			throw new IllegalStateException("Absent declaration style has no kind");
		}

		@Override
		public PsiElement versionElement() {
			throw new IllegalStateException("Absent declaration style has no version element");
		}

		@Override
		public @Nullable PsiElement owningCall() {
			throw new IllegalStateException("Absent declaration style has no owning call");
		}

	}

}
