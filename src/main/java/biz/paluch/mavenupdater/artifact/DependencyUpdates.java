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
package biz.paluch.mavenupdater.artifact;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Result of scanning dependencies and checking for updates.
 */
public record DependencyUpdates(String projectName, List<DependencyUpdateOption> updates,
		List<DependencyUpdateOption> uniqueUpdates, List<String> errors) {

	public DependencyUpdates(String projectName, List<DependencyUpdateOption> items, List<String> errors) {
		this(projectName, items, getUniqueItems(items), errors);
	}

	public List<DependencyUpdateOption> getUpdates() {
		return uniqueUpdates();
	}

	private static List<DependencyUpdateOption> getUniqueItems(List<DependencyUpdateOption> items) {

		List<DependencyUpdateOption> unique = new ArrayList<>();
		Set<VersionSource> propertyVersionSources = new LinkedHashSet<>();

		for (DependencyUpdateOption item : items) {

			boolean skip = false;
			for (VersionSource source : item.getCandidate().getVersionSources()) {
				if (!propertyVersionSources.add(source)) {
					skip = true;
					break;
				}
			}

			if (!skip) {
				unique.add(item);
			}
		}

		return unique;
	}
}
