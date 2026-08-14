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

package biz.paluch.dap.maven.wrapper;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.security.DigestOutputStream;
import java.util.HexFormat;
import java.util.function.Consumer;

import biz.paluch.dap.util.HttpClientUtil;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.util.io.HttpRequests;
import com.intellij.util.net.NetUtils;
import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.io.output.NullOutputStream;
import org.apache.http.HttpHeaders;
import org.jspecify.annotations.Nullable;

/**
 * Downloads a wrapper artifact and computes its SHA-256 checksum.
 *
 * @author Mark Paluch
 */
class WrapperChecksumDownloader {

	private WrapperChecksumDownloader() {
	}

	static void downloadAndComputeSha(Project project, String url, Consumer<String> success,
			Consumer<IOException> failure, Runnable canceled) {

		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException ex) {
			failure.accept(new IOException("Invalid wrapper URL", ex));
			return;
		}

		new Task.Backgroundable(project, MessageBundle.message("wrapper.checksum.task"), true) {

			private @Nullable String result;

			private @Nullable IOException error;

			@Override
			public void run(ProgressIndicator indicator) {
				try {
					result = downloadAndComputeSha(uri, indicator);
				} catch (IOException ex) {
					error = ex;
				}
			}

			@Override
			public void onSuccess() {
				if (project.isDisposed()) {
					canceled.run();
				} else if (error != null) {
					failure.accept(error);
				} else if (result != null) {
					success.accept(result);
				}
			}

			@Override
			public void onCancel() {
				canceled.run();
			}

			@Override
			public void onThrowable(Throwable error) {
				canceled.run();
			}

		}.queue();
	}

	static String downloadAndComputeSha(URI uri, ProgressIndicator indicator) throws IOException {

		return HttpRequests.request(uri.toASCIIString())
				.userAgent(HttpClientUtil.getUserAgent())
				.connectTimeout(HttpClientUtil.CONNECT_TIMEOUT_MS)
				.readTimeout(HttpClientUtil.READ_TIMEOUT_MS)
				.connect(request -> {

					DigestOutputStream dos = new DigestOutputStream(NullOutputStream.INSTANCE,
							DigestUtils.getSha256Digest());
					URLConnection connection = request.getConnection();

					long contentLength = contentLength(connection.getHeaderField(HttpHeaders.CONTENT_LENGTH));
					try (InputStream in = request.getInputStream()) {

						NetUtils.copyStreamContent(indicator, in, dos, contentLength);
						return HexFormat.of().formatHex(dos.getMessageDigest().digest());
					}
				});
	}

	private static long contentLength(@Nullable String header) {

		if (header == null) {
			return -1;
		}
		try {
			long contentLength = Long.parseLong(header.trim());
			return contentLength >= 0 ? contentLength : -1;
		} catch (NumberFormatException ex) {
			return -1;
		}
	}

}
