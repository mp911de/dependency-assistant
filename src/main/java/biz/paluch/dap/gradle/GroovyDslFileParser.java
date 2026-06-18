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

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import biz.paluch.dap.support.ArtifactDeclaration;
import biz.paluch.dap.support.Property;
import biz.paluch.dap.support.PropertyResolver;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.jetbrains.plugins.groovy.lang.psi.api.statements.expressions.GrMethodCall;
import org.jspecify.annotations.Nullable;

/**
 * Operation-scoped parser for declarations in one Groovy DSL Gradle file. Local
 * {@code ext} properties and script-level variables are parsed once and take
 * precedence over the inherited property resolver for every declaration in the
 * file.
 *
 * @author Mark Paluch
 * @see GroovyDslParser
 */
class GroovyDslFileParser {

	private final PsiFile file;

	private final PropertyResolver propertyResolver;

	private final Set<String> declaredPropertyNames;

	private final GroovyDslParser parser;

	GroovyDslFileParser(PsiFile file, PropertyResolver inheritedProperties) {
		this(file, inheritedProperties, VersionCatalogRegistry.from(file));
	}

	GroovyDslFileParser(PsiFile file, PropertyResolver inheritedProperties, VersionCatalogRegistry registry) {

		GroovyDslPropertyResolver propertyResolver = GroovyDslPropertyResolver.from(file);

		this.file = file;
		this.propertyResolver = propertyResolver.withFallback(inheritedProperties);
		this.declaredPropertyNames = propertyResolver.getDeclaredPropertyNames();
		this.parser = new GroovyDslParser(this.propertyResolver, registry);
	}

	public @Nullable ArtifactDeclaration parse(GrMethodCall call) {
		return parser.parse(call);
	}

	List<ArtifactDeclaration> parseDeclarations() {
		List<ArtifactDeclaration> declarations = new ArrayList<>();
		SyntaxTraverser.psiTraverser(file).filter(GrMethodCall.class)
				.filterMap(this::parse)
				.forEach(declarations::add);
		return List.copyOf(declarations);
	}

	Set<String> getDeclaredPropertyNames() {
		return declaredPropertyNames;
	}

	@Nullable
	TomlReference findCatalogReference(GrMethodCall call) {
		return parser.findCatalogReference(call);
	}

	boolean containsProperty(String name) {
		return propertyResolver.containsProperty(name);
	}

	@Nullable
	Property getPropertyValue(String name) {
		return propertyResolver.getPropertyValue(name);
	}

}
