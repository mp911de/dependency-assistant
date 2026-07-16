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

package biz.paluch.dap.assistant.completion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.Release;
import biz.paluch.dap.artifact.Releases;
import biz.paluch.dap.util.Sequence;

/**
 * The development lines of a version history, newest line first.
 *
 * <p>Lines are formed by walking the history in its canonical newest-first
 * order and splitting whenever adjacent versions no longer share their major
 * and minor (or release train). Duplicate versions are skipped.
 *
 * @author Mark Paluch
 * @see DevelopmentLine
 */
class DevelopmentLines implements Sequence<DevelopmentLine> {

	private final List<DevelopmentLine> lines;

	private DevelopmentLines(List<DevelopmentLine> lines) {
		this.lines = lines;
	}

	/**
	 * Group the given release history into development lines.
	 *
	 * @param releases the release history to group.
	 * @return the development lines, newest line first.
	 */
	public static DevelopmentLines of(Releases releases) {
		return of(releases.stream().map(Release::version).toList());
	}

	/**
	 * Group the given versions into development lines.
	 *
	 * @param versions the versions in newest-first order.
	 * @return the development lines, newest line first.
	 */
	public static DevelopmentLines of(List<ArtifactVersion> versions) {

		List<DevelopmentLine> lines = new ArrayList<>();
		List<ArtifactVersion> current = new ArrayList<>();
		Set<String> seen = new HashSet<>();

		for (ArtifactVersion version : versions) {

			ArtifactVersion unwrapped = version.unwrap();
			if (!seen.add(unwrapped.toString())) {
				continue;
			}

			if (!current.isEmpty()
					&& !current.getFirst().unwrap().hasSameMajorMinor(unwrapped)) {
				lines.add(new DevelopmentLine(current));
				current = new ArrayList<>();
			}
			current.add(version);
		}

		if (!current.isEmpty()) {
			lines.add(new DevelopmentLine(current));
		}
		return new DevelopmentLines(lines);
	}

	/**
	 * Return whether this history forms no development lines.
	 *
	 * @return {@literal true} if there are no lines; {@literal false} otherwise.
	 */
	@Override
	public boolean isEmpty() {
		return lines.isEmpty();
	}

	/**
	 * Return the number of development lines.
	 */
	public int size() {
		return lines.size();
	}

	/**
	 * Return the development lines as a list, newest line first.
	 *
	 * @return the lines.
	 */
	public List<DevelopmentLine> getLines() {
		return lines;
	}

	@Override
	public Iterator<DevelopmentLine> iterator() {
		return lines.iterator();
	}

	/**
	 * Return the development lines as a stream, newest line first.
	 */
	@Override
	public Stream<DevelopmentLine> stream() {
		return lines.stream();
	}

	@Override
	public String toString() {
		return "DevelopmentLines" + lines;
	}

}
