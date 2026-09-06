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

package biz.paluch.dap.assistant.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.security.DigestOutputStream;
import java.util.HexFormat;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletableFuture;

import biz.paluch.dap.notify.Notifications;
import biz.paluch.dap.util.HttpClientUtil;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.openapi.application.ApplicationManager;
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
 * Background checksum downloads for dependency upgrades.
 *
 * @author Mark Paluch
 */
public class ChecksumDownloader {

	protected ChecksumDownloader() {
	}

	public static ChecksumDownloader getInstance() {
		return ApplicationManager.getApplication().getService(ChecksumDownloader.class);
	}

	/**
	 * Queue a cancellable artifact download and compute its lowercase SHA-256
	 * digest.
	 * <p>Download task completion is delivered on the EDT. Invalid URLs fail
	 * immediately. Failures complete the future exceptionally, including task
	 * cancellation or project disposal.
	 */
	public CompletableFuture<String> computeSha(Project project, String url) {

		CompletableFuture<String> future = doComputeSha(project, url);

		return future.whenComplete((sha, failure) -> {
			if (failure != null) {
				notifyFailure(project, url, failure);
			}
		});
	}

	private CompletableFuture<String> doComputeSha(Project project, String url) {
		CompletableFuture<String> future = new CompletableFuture<>();

		URI uri;
		try {
			uri = URI.create(url);
		} catch (IllegalArgumentException ex) {
			future.completeExceptionally(new IOException("Invalid wrapper URL", ex));
			return future;
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
				if (project.isDisposed() || result == null && error == null) {
					future.cancel(false);
				} else if (error != null) {
					future.completeExceptionally(error);
				} else {
					future.complete(result);
				}
			}

			@Override
			public void onCancel() {
				future.cancel(false);
			}

			@Override
			public void onThrowable(Throwable error) {
				future.completeExceptionally(error);
			}

		}.queue();

		return future;
	}

	private static void notifyFailure(Project project, String url, Throwable failure) {

		if (failure instanceof CancellationException || project.isDisposed()) {
			return;
		}
		Notifications.error(MessageBundle.message("wrapper.checksum.error.title"),
				MessageBundle.message("wrapper.checksum.error", url, Notifications.errorMessage(failure)))
				.notify(project);
	}

	/**
	 * Download the artifact and return its lowercase SHA-256 digest.
	 * @throws IOException if the request or response stream fails.
	 */
	public static String downloadAndComputeSha(URI uri, ProgressIndicator indicator) throws IOException {

		return HttpRequests.request(uri.toASCIIString())
				.userAgent(HttpClientUtil.getUserAgent())
				.connectTimeout(HttpClientUtil.CONNECT_TIMEOUT_MS)
				.readTimeout(HttpClientUtil.READ_TIMEOUT_MS)
				.connect(request -> {

					DigestOutputStream dos = new DigestOutputStream(NullOutputStream.INSTANCE,
							DigestUtils.getSha256Digest());
					URLConnection connection = request.getConnection();

					long contentLength = getContentLength(connection.getHeaderField(HttpHeaders.CONTENT_LENGTH));
					try (InputStream in = request.getInputStream()) {

						NetUtils.copyStreamContent(indicator, in, dos, contentLength);
						return HexFormat.of().formatHex(dos.getMessageDigest().digest());
					}
				});
	}

	private static long getContentLength(@Nullable String header) {

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
