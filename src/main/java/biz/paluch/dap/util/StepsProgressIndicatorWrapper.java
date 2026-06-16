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

import com.intellij.ide.util.DelegatingProgressIndicator;

/**
 * Adapter exposing the platform {@code StepsProgressIndicator} through the
 * {@link StepsProgressIndicator} interface. Progress calls forward to the
 * wrapped platform indicator, and {@link #nextStep()} delegates to its step
 * advance.
 */
class StepsProgressIndicatorWrapper extends DelegatingProgressIndicator implements StepsProgressIndicator {

	private final com.intellij.util.progress.StepsProgressIndicator delegate;

	StepsProgressIndicatorWrapper(com.intellij.util.progress.StepsProgressIndicator delegate) {
		super(delegate);
		this.delegate = delegate;
	}

	@Override
	public void nextStep() {
		delegate.nextStep();
	}

}
