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

import biz.paluch.dap.artifact.DeclarationSource;

/**
 * Context for a Gradle dependency-configuration call, exposing its
 * {@link DeclarationSource} and configuration name.
 *
 * @author Mark Paluch
 * @see KotlinDslParser.KotlinDeclarationCall
 * @see GroovyDslParser.GroovyDeclarationCall
 * @see DeclarationSource
 */
interface ConfigurationContext {

	/**
	 * Return the {@link DeclarationSource} determining how and where the call
	 * declares its artifact (direct dependency, managed platform, plugin, or plugin
	 * management).
	 *
	 * @return the declaration source.
	 */
	DeclarationSource getDeclarationSource();

	/**
	 * Return the name of the Gradle configuration represented by this context.
	 *
	 * @return the configuration name.
	 */
	String getConfigurationName();

	/**
	 * Return whether this context represents a plugin declaration.
	 *
	 * @return {@literal true} if the declaration source is a direct or managed
	 * plugin declaration; {@literal false} otherwise.
	 */
	default boolean isPlugin() {
		return getDeclarationSource() instanceof DeclarationSource.Plugin;
	}

	/**
	 * Return whether this context represents a library dependency declaration.
	 *
	 * @return {@literal true} if the declaration source is a direct dependency or
	 * managed platform; {@literal false} otherwise.
	 */
	default boolean isDependency() {
		return getDeclarationSource() instanceof DeclarationSource.Dependency;
	}

}
