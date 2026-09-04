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

package biz.paluch.dap.util;

import java.util.concurrent.CancellationException;
import java.util.concurrent.Future;

import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.util.ProgressIndicatorUtils;

/**
 * Utility to await futures.
 *
 * @author Mark Paluch
 */
public class Await {

	/**
	 * Await the given future completion (or cancellation) and return the completion
	 * value or throw {@link ProcessCanceledException} if the future is cancelled.
	 */
	public static <T> T await(Future<T> future, ProgressIndicator indicator) {
		try {
			return ProgressIndicatorUtils.awaitWithCheckCanceled(future, indicator);
		} catch (ProcessCanceledException e) {
			throw e;
		} catch (CancellationException e) {
			throw new ProcessCanceledException(e);
		}
	}

}
