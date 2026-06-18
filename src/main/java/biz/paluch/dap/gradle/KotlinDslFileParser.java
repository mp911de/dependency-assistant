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
import org.jetbrains.kotlin.psi.KtCallElement;
import org.jspecify.annotations.Nullable;

/**
 * Parser for declarations in a Kotlin DSL Gradle file. Local Kotlin and extra
 * properties take precedence over the inherited property resolver.
 *
 * @author Mark Paluch
 * @see KotlinDslParser
 */
class KotlinDslFileParser {

	private final PsiFile file;

	private final PropertyResolver propertyResolver;

	private final Set<String> extraPropertyNames;

	private final KotlinDslParser parser;

	KotlinDslFileParser(PsiFile file, PropertyResolver inheritedProperties) {
		this(file, inheritedProperties, VersionCatalogRegistry.from(file));
	}

	KotlinDslFileParser(PsiFile file, PropertyResolver inheritedProperties, VersionCatalogRegistry registry) {

		KotlinDslPropertyResolver propertyResolver = KotlinDslPropertyResolver.from(file);

		this.file = file;
		this.propertyResolver = propertyResolver.withFallback(inheritedProperties);
		this.extraPropertyNames = propertyResolver.getExtraPropertyNames();
		this.parser = new KotlinDslParser(this.propertyResolver, registry);
	}

	/**
	 * Parse the given call element into an {@link ArtifactDeclaration}.
	 * @param call the call element to parse.
	 * @return the parsed declaration, or {@literal null} when the call is not
	 * supported.
	 */
	public @Nullable ArtifactDeclaration parse(KtCallElement call) {
		return parser.parse(call);
	}

	List<ArtifactDeclaration> parseDeclarations() {
		List<ArtifactDeclaration> declarations = new ArrayList<>();
		SyntaxTraverser.psiTraverser(file).filter(KtCallElement.class)
				.filterMap(this::parse)
				.forEach(declarations::add);
		return List.copyOf(declarations);
	}

	Set<String> getExtraPropertyNames() {
		return extraPropertyNames;
	}

	@Nullable
	TomlReference findCatalogReference(KtCallElement call) {
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
