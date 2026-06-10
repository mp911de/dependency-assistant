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
import java.util.List;

import com.intellij.openapi.vfs.VirtualFile;

/**
 * Result of a dependency check over one or more build files.
 *
 * <p>The result contains sorted update candidates, the scanned files that
 * produced those candidates, and non-fatal lookup errors collected while
 * resolving release metadata.
 *
 * @author Mark Paluch
 * @param candidates the update candidates that can be offered to the user.
 * @param files the build files included in the dependency check.
 * @param errors non-fatal release lookup errors; empty when all lookups
 * succeeded.
 */
public record DependencyCheckResult(List<UpgradeCandidate> candidates, Collection<VirtualFile> files,
		List<String> errors) {
}
