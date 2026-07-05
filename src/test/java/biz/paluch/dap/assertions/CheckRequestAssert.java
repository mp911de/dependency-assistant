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

package biz.paluch.dap.assertions;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.checker.CheckRequest;
import org.assertj.core.api.AbstractAssert;

/**
 * AssertJ assertions for {@link CheckRequest}.
 *
 * @author Mark Paluch
 */
public class CheckRequestAssert extends AbstractAssert<CheckRequestAssert, CheckRequest> {

	CheckRequestAssert(CheckRequest actual) {
		super(actual, CheckRequestAssert.class);
	}

	/**
	 * Verifies that the request contains exactly one package entry for {@code pkg}
	 * requesting exactly the given versions in order.
	 * @param pkg the package expected to be requested.
	 * @param versions the exact versions expected to be requested, in order.
	 * @return this assertion object.
	 */
	public CheckRequestAssert requestsExactly(PackageIdentity pkg, String... versions) {
		isNotNull();

		Map<PackageIdentity, List<ArtifactVersion>> requested = new LinkedHashMap<>();
		actual.forEach(requested::put);

		List<ArtifactVersion> expected = Arrays.stream(versions).map(ArtifactVersion::of).toList();
		Assertions.assertThat(requested).containsExactly(Assertions.entry(pkg, expected));
		return this;
	}

}
