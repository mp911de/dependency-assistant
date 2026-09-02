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

import java.util.List;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.newvfs.BulkFileListener;
import com.intellij.openapi.vfs.newvfs.events.VFileDeleteEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileEvent;
import com.intellij.openapi.vfs.newvfs.events.VFileMoveEvent;
import com.intellij.openapi.vfs.newvfs.events.VFilePropertyChangeEvent;
import org.jspecify.annotations.Nullable;

/**
 * Evicts project state when build files disappear from their recorded path.
 *
 * @author Mark Paluch
 */
public class ProjectFileRemovalListener implements BulkFileListener {

	private final Project project;

	private final StateService service;

	public ProjectFileRemovalListener(Project project) {
		this.project = project;
		this.service = StateService.getInstance(project);

	}

	@Override
	public void before(List<? extends VFileEvent> events) {

		if (project.isDisposed() || project.isDefault()) {
			return;
		}

		StateService service = null;
		for (VFileEvent event : events) {

			String path = getRemovedPath(event);
			if (path == null) {
				continue;
			}
			evict(path);
		}
	}

	private void evict(String path) {

		Cache cache = service.getCache();
		for (ProjectCache entry : cache.getProjects()) {

			ProjectId identity = entry.getId();
			if (!isAtOrBelow(identity.buildFile(), path)) {
				continue;
			}
			service.getProjectState(identity).remove();
		}
	}

	/**
	 * Return whether the descriptor is the given path or lies below it. The
	 * separator guard keeps {@code /repo} from matching {@code /repository}.
	 */
	private static boolean isAtOrBelow(@Nullable String descriptor, String path) {

		if (descriptor == null) {
			return false;
		}
		if (descriptor.equals(path)) {
			return true;
		}
		return descriptor.startsWith(path.endsWith("/") ? path : path + "/");
	}

	/**
	 * Return the path a file ceases to occupy through the given event.
	 *
	 * @param event the file system event.
	 * @return the former path for a deletion, move, or rename; {@literal null} for
	 * any other event.
	 */
	private static @Nullable String getRemovedPath(VFileEvent event) {

		if (event instanceof VFileDeleteEvent) {
			return event.getPath();
		}
		if (event instanceof VFileMoveEvent move) {
			return move.getOldPath();
		}
		if (event instanceof VFilePropertyChangeEvent change
				&& VirtualFile.PROP_NAME.equals(change.getPropertyName())) {
			return change.getOldPath();
		}
		return null;
	}

}
