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

package biz.paluch.dap.fixtures;

import java.util.Arrays;

import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;

/**
 * Test factory for {@link Releases} built from plain version strings.
 *
 * @author Mark Paluch
 */
public class TestReleases {

	/**
	 * Create {@link Releases} from the given version strings.
	 *
	 * @param versions the version strings, each parsed through
	 * {@link Release#of(String)}.
	 * @return the releases for the given versions.
	 */
	public static Releases from(String... versions) {
		return Releases.of(Arrays.stream(versions).map(Release::of).toList());
	}

}
