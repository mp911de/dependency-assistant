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

import java.io.IOException;

import com.intellij.openapi.util.text.StringUtil;

/**
 * Thrown when a response body exceeds the size a fetch accepts.
 *
 * <p>Callers can catch this to fall back to a smaller representation of the
 * same resource rather than failing the fetch.
 *
 * @author Mark Paluch
 * @see HttpClientUtil#capped(java.io.InputStream, int)
 */
public class ResponseTooLargeException extends IOException {

	/**
	 * Create a new {@code ResponseTooLargeException} for the exceeded cap.
	 *
	 * @param maxBytes the number of bytes the fetch accepted.
	 */
	public ResponseTooLargeException(int maxBytes) {
		super("Response body exceeds %s".formatted(StringUtil.formatFileSize(maxBytes)));
	}

}
