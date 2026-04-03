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
package biz.paluch.dap;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactRelease;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.VersionAge;
import biz.paluch.dap.state.Cache;

import java.util.Collection;
import java.util.List;
import java.util.function.Function;

import org.jspecify.annotations.Nullable;

import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.PrioritizedLookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;

/**
 * Utility to find and add suggestions to a {@link CompletionResultSet}.
 *
 * @author Mark Paluch
 */
public abstract class SuggestionProviderUtil {

	public static List<ArtifactRelease> findOptions(ArtifactId artifactId, Cache cache) {

		List<Release> versions = cache.getReleases(artifactId, false);
		List<ArtifactRelease> result = new java.util.ArrayList<>();

		for (Release release : versions) {
			result.add(new ArtifactRelease(artifactId, release));
		}

		return result;
	}

	public static void addSuggestions(Collection<ArtifactRelease> versions, CompletionResultSet result,
			Function<ArtifactId, String> toString, biz.paluch.dap.artifact.@Nullable ArtifactVersion currentVersion) {

		double priority = versions.size();

		for (ArtifactRelease option : versions) {

			String typeText = toString.apply(option.artifactId());
			Release version = option.release();

			LookupElementBuilder element = LookupElementBuilder.create(version.version().toString()).withTypeText(typeText);

			if (version.releaseDate() != null) {
				element = element.withTailText(" (" + version.releaseDate().toLocalDate() + ")", true);
			}

			if (currentVersion != null) {

				if (option.isNewer(currentVersion)) {
					element = element.bold();
				}
				if (option.isOlder(currentVersion)) {
					element = element.withItemTextItalic(true);
				}

				VersionAge versionAge = VersionAge.fromVersions(currentVersion, option);
				element = element.withIcon(versionAge.getIcon());
			}

			result.addElement(PrioritizedLookupElement.withPriority(element, priority--));
		}
	}

}
