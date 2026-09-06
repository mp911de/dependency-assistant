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

package biz.paluch.dap.assistant.completion;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.stream.Stream;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.VersionAware;
import biz.paluch.dap.util.Sequence;

/**
 * Development lines ordered newest first.
 * <p>Versions with the same unwrapped rendering are represented once.
 *
 * @author Mark Paluch
 */
class DevelopmentLines implements Sequence<DevelopmentLine> {

	private final List<DevelopmentLine> lines;

	private DevelopmentLines(List<DevelopmentLine> lines) {
		this.lines = lines;
	}

	/**
	 * Group a newest-first release history into development lines.
	 */
	public static DevelopmentLines of(Sequence<? extends VersionAware> releases) {
		return of(releases.stream().map(VersionAware::getVersion).toList());
	}

	/**
	 * Group versions into development lines.
	 * @param versions the versions in newest-first order.
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

	@Override
	public boolean isEmpty() {
		return lines.isEmpty();
	}

	public int size() {
		return lines.size();
	}

	/**
	 * Return the live, mutable list of lines, newest first.
	 */
	public List<DevelopmentLine> getLines() {
		return lines;
	}

	@Override
	public Iterator<DevelopmentLine> iterator() {
		return lines.iterator();
	}

	@Override
	public Stream<DevelopmentLine> stream() {
		return lines.stream();
	}

	@Override
	public String toString() {
		return "DevelopmentLines" + lines;
	}

}
