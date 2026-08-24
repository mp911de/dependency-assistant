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

package biz.paluch.dap.util;

import java.io.File;

import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.Contract;
import org.jspecify.annotations.Nullable;

/**
 * Utility methods for testing whether local and virtual file handles refer to
 * existing files or directories.
 *
 * @author Mark Paluch
 * @see File
 * @see VirtualFile
 */
public class FileUtils {

	/**
	 * Determine whether the given virtual file is a valid, existing directory.
	 *
	 * @param directory the virtual file to inspect.
	 * @return {@code true} if the virtual file is a valid, existing directory.
	 */
	@Contract("null -> false")
	public static boolean isDirectory(@Nullable VirtualFile directory) {
		return directory != null && directory.isValid() && directory.isDirectory() && directory.exists();
	}

	/**
	 * Determine whether the given virtual file is a valid, existing non-directory.
	 *
	 * @param file the virtual file to inspect.
	 * @return {@code true} if the virtual file is valid, exists, and is not a
	 * directory.
	 */
	@Contract("null -> false")
	public static boolean isFile(@Nullable VirtualFile file) {
		return file != null && file.isValid() && !file.isDirectory() && file.exists();
	}

	/**
	 * Determine whether the given local file is an existing directory.
	 *
	 * @param directory the local file to inspect.
	 * @return {@code true} if the local file is an existing directory.
	 */
	@Contract("null -> false")
	public static boolean isDirectory(@Nullable File directory) {
		return directory != null && directory.isDirectory() && directory.exists();
	}

	/**
	 * Determine whether the given local file is an existing regular file.
	 *
	 * @param file the local file to inspect.
	 * @return {@code true} if the local file is an existing regular file.
	 */
	@Contract("null -> false")
	public static boolean isFile(@Nullable File file) {
		return file != null && file.isFile() && file.exists();
	}

}
