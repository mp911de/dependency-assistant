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

package biz.paluch.dap.assistant;

import java.util.Collection;
import java.util.Set;
import java.util.TreeSet;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.rule.ArtifactPattern;
import biz.paluch.dap.state.StateService;
import com.intellij.openapi.project.Project;
import org.jetbrains.io.JsonUtil;

/**
 * Renders a starter {@code dependencyfile.json} descriptor.
 *
 * @author Mark Paluch
 */
class DependencyfileTemplate {

	private DependencyfileTemplate() {
	}

	private static Set<ArtifactId> getArtifactIds(StateService service) {

		TreeSet<ArtifactId> artifactIds = new TreeSet<>(ArtifactId.COMPARATOR);
		service.doWithDependencies(dependency -> artifactIds.add(dependency.getArtifactId()));
		return artifactIds;
	}

	static String render(Project project) {
		return render(getArtifactIds(StateService.getInstance(project)));
	}

	static String render(Collection<? extends ArtifactId> artifactIds) {

		TreeSet<String> patterns = new TreeSet<>();
		for (ArtifactId artifactId : artifactIds) {
			patterns.add(ArtifactPattern.keyFor(artifactId));
		}

		StringBuilder json = new StringBuilder("{\n  \"artifacts\": {");
		if (patterns.isEmpty()) {
			return json.append("}\n}\n").toString();
		}

		json.append("\n");
		int index = 0;
		for (String pattern : patterns) {
			json.append("    ");
			JsonUtil.escape(pattern, json);
			json.append(": {\n      \"name\": ");
			String name = pattern.startsWith("@") ? pattern.substring(1) : pattern;
			JsonUtil.escape(name, json);
			json.append("\n    }");
			json.append(++index < patterns.size() ? ",\n" : "\n");
		}
		return json.append("  }\n}\n").toString();
	}

}
