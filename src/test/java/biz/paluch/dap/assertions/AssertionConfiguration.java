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

package biz.paluch.dap.assertions;

import com.intellij.codeInsight.daemon.LineMarkerInfo;
import org.assertj.core.configuration.Configuration;
import org.assertj.core.presentation.Representation;
import org.assertj.core.presentation.StandardRepresentation;

public class AssertionConfiguration extends Configuration {

	public static final LineMarkerInfoRepresentation LINE_MARKER = new LineMarkerInfoRepresentation();

	public static final StandardRepresentation REPRESENTATION = new StandardRepresentation();

	static {

		StandardRepresentation.registerFormatterForType(LineMarkerInfo.class, LINE_MARKER::toStringOf);
		StandardRepresentation.registerFormatterForType(LineMarkerInfo.LineMarkerGutterIconRenderer.class,
				LINE_MARKER::toStringOf);
	}

	@Override
	public Representation representation() {
		return REPRESENTATION;
	}

	@Override
	public void applyAndDisplay() {
		super.apply();
	}

}
