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

package biz.paluch.dap.state;

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.util.StringUtils;
import com.intellij.util.xmlb.Converter;
import org.jspecify.annotations.Nullable;

/**
 * xmlb converter collapsing a list of artifact identifiers into a single
 * comma-separated attribute value.
 * <p>Maven artifact identifiers are restricted to {@code [A-Za-z0-9_.-]}, so
 * the comma cannot occur inside a value and needs no escaping. Identifiers
 * containing the delimiter are rejected on write rather than producing an
 * attribute that cannot be read back.
 * <p>An empty list serializes to {@literal null}, which makes the platform omit
 * the attribute entirely rather than writing an empty value.
 *
 * @author Mark Paluch
 */
class ArtifactIdsConverter extends Converter<List<String>> {

	static final String DELIMITER = ",";

	@Override
	public List<String> fromString(String value) {

		List<String> artifactIds = new ArrayList<>();
		for (String artifactId : value.split(DELIMITER)) {
			if (StringUtils.hasText(artifactId)) {
				artifactIds.add(artifactId.trim());
			}
		}
		return artifactIds;
	}

	@Override
	public @Nullable String toString(List<String> value) {

		if (value.isEmpty()) {
			return null;
		}

		StringBuilder joined = new StringBuilder();
		for (String artifactId : value) {
			if (artifactId.contains(DELIMITER)) {
				throw new IllegalArgumentException(
						"ArtifactId '%s' must not contain '%s'".formatted(artifactId, DELIMITER));
			}
			if (!joined.isEmpty()) {
				joined.append(DELIMITER);
			}
			joined.append(artifactId);
		}
		return joined.toString();
	}

}
