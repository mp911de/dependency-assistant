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

package biz.paluch.dap.rule;

import java.util.List;

import biz.paluch.dap.support.MessageBundle;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.jetbrains.jsonSchema.extension.JsonSchemaFileProvider;
import com.jetbrains.jsonSchema.extension.JsonSchemaProviderFactory;
import com.jetbrains.jsonSchema.extension.SchemaType;
import com.jetbrains.jsonSchema.impl.JsonSchemaVersion;
import org.jspecify.annotations.Nullable;

/**
 * Serves the bundled JSON schema for {@code dependencyfile.json} so the editor
 * validates and completes Dependency Rules descriptors without manual schema
 * mapping.
 *
 * @author Mark Paluch
 * @see RuleParser
 */
public class DependencyfileSchemaProviderFactory implements JsonSchemaProviderFactory, DumbAware {

	private static final String SCHEMA_RESOURCE = "/schemas/dependencyfile.schema.json";

	@Override
	public List<JsonSchemaFileProvider> getProviders(Project project) {
		return List.of(new DependencyfileSchemaProvider());
	}

	private static class DependencyfileSchemaProvider implements JsonSchemaFileProvider {

		@Override
		public boolean isAvailable(VirtualFile file) {
			return RuleService.FILE_NAME.equals(file.getName());
		}

		@Override
		public String getName() {
			return MessageBundle.message("dependencyfile.schema.display-name");
		}

		@Override
		public @Nullable VirtualFile getSchemaFile() {
			return JsonSchemaProviderFactory.getResourceFile(DependencyfileSchemaProviderFactory.class, SCHEMA_RESOURCE);
		}

		@Override
		public SchemaType getSchemaType() {
			return SchemaType.embeddedSchema;
		}

		@Override
		public JsonSchemaVersion getSchemaVersion() {
			return JsonSchemaVersion.SCHEMA_7;
		}

	}

}
