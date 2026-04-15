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
package biz.paluch.dap.support;

import java.util.function.Consumer;

import biz.paluch.dap.artifact.ArtifactId;
import org.jspecify.annotations.Nullable;

import org.springframework.util.Assert;

/**
 * Value object representing a reference to an artifact declaration.
 *
 * @author Mark Paluch
 */
public class ArtifactReference {

	private static final ArtifactReference UNRESOLVED = new ArtifactReference(null);

	private final @Nullable ArtifactDeclaration declaration;

	private ArtifactReference(@Nullable ArtifactDeclaration declaration) {
		this.declaration = declaration;
	}

	/**
	 * Empty lookup result (artifact not found or not resolvable).
	 */
	public static ArtifactReference unresolved() {
		return UNRESOLVED;
	}

	/**
	 * Create an {@code ArtifactReference} from the given builder consumer. The
	 * consumer is used to populate the {@link ArtifactDeclaration.Builder}.
	 */
	public static ArtifactReference from(Consumer<ArtifactDeclaration.Builder> builderConsumer) {
		ArtifactDeclaration.Builder builder = ArtifactDeclaration.builder();
		builderConsumer.accept(builder);
		return new ArtifactReference(builder.build());
	}

	/**
	 * Returns whether the artifact reference is present and resolved.
	 */
	public boolean isResolved() {
		return declaration != null;
	}

	/**
	 * Returns the {@link ArtifactDeclaration} or throws an exception if none is
	 * present.
	 */
	public ArtifactDeclaration getDeclaration() {

		Assert.state(declaration != null, "No declaration available");
		return declaration;
	}

	public ArtifactId getArtifactId() {
		return getDeclaration().getArtifactId();
	}

	@Override
	public String toString() {

		if (declaration == null) {
			return "Unresolved";
		}

		return "Resolved: " + declaration;
	}

}
