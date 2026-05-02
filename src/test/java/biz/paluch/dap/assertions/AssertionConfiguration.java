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

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import org.assertj.core.configuration.Configuration;
import org.assertj.core.presentation.Representation;
import org.assertj.core.presentation.StandardRepresentation;

/**
 * AssertJ configuration used by Dependency Assistant tests.
 *
 * <p>The configuration installs {@link LineMarkerInfoRepresentation} into
 * AssertJ's standard representation so gutter assertion failures show the
 * underlying line marker tooltip and PSI element text. The configuration is
 * discovered by AssertJ's configuration mechanism during test execution.
 *
 * @author Mark Paluch
 */
public class AssertionConfiguration extends Configuration {

	/**
	 * Formatter for IntelliJ line marker types.
	 */
	public static final LineMarkerInfoRepresentation LINE_MARKER = new LineMarkerInfoRepresentation();

	/**
	 * Shared AssertJ representation used by the test suite.
	 */
	public static final StandardRepresentation REPRESENTATION = new StandardRepresentation();

	static {

		StandardRepresentation.registerFormatterForType(LineMarkerInfo.class, LINE_MARKER::toStringOf);
		StandardRepresentation.registerFormatterForType(LineMarkerInfo.LineMarkerGutterIconRenderer.class,
				LINE_MARKER::toStringOf);
	}

	/**
	 * Returns the shared representation with Dependency Assistant formatters
	 * installed.
	 */
	@Override
	public Representation representation() {
		return REPRESENTATION;
	}

	/**
	 * Applies this configuration without printing AssertJ's default configuration
	 * banner during test execution.
	 */
	@Override
	public void applyAndDisplay() {
		super.apply();
	}

}
