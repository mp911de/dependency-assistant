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

package biz.paluch.dap.checker;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.packageChecker.model.Package;
import com.intellij.packageChecker.service.PackageChecker;
import com.intellij.packageChecker.service.PackageStatus;
import kotlin.Lazy;
import kotlin.LazyKt;
import kotlin.coroutines.Continuation;
import kotlin.coroutines.EmptyCoroutineContext;
import kotlin.jvm.functions.Function2;
import kotlinx.coroutines.CoroutineScope;
import kotlinx.coroutines.CoroutineStart;
import kotlinx.coroutines.future.FutureKt;
import org.jspecify.annotations.Nullable;

/**
 * Bridge to the private bulk-scan function of {@link PackageChecker}.
 *
 * <p>Package Checker exposes no public API to scan a batch of packages, so the
 * suspending {@code checkPackages} function is invoked reflectively and its
 * coroutine adapted to a {@link CompletableFuture}. The reflective target is
 * resolved once on first use. An IDE update that changes the signature renders
 * the delegate {@link #isAvailable() unavailable} rather than failing the
 * enclosing scan.
 *
 * <p>Resolution touches Package Checker classes, so the delegate must only be
 * used while that plugin is installed and enabled.
 *
 * @author Mark Paluch
 * @see PackageCheckerVulnerabilitySource
 */
abstract class PackageCheckerDelegate {

	private static final Logger LOG = Logger.getInstance(PackageCheckerDelegate.class);

	private static final Lazy<@Nullable Method> CHECK_PACKAGES = LazyKt
			.lazy(PackageCheckerDelegate::detectCheckPackages);

	private static @Nullable Method detectCheckPackages() {

		try {
			Method checkPackages = PackageChecker.class.getDeclaredMethod("checkPackages", Iterable.class,
					Continuation.class);
			checkPackages.setAccessible(true);
			return checkPackages;
		} catch (NoSuchMethodException e) {
			// private method, signature can change on any IDE update
			LOG.warn("PackageChecker.checkPackages signature changed", e);
			return null;
		}
	}

	/**
	 * Return whether the running IDE provides the expected bulk-scan function.
	 *
	 * @return {@literal true} if packages can be scanned; {@literal false}
	 * otherwise.
	 */
	static boolean isAvailable() {
		return CHECK_PACKAGES.getValue() != null;
	}

	/**
	 * Scan the given packages and return their resulting status.
	 *
	 * <p>The returned map contains only the packages Package Checker answered for.
	 * The future completes exceptionally when the scan fails or when the delegate
	 * is not {@link #isAvailable() available}.
	 *
	 * @param checker the Package Checker service to scan through.
	 * @param packages the packages to scan.
	 * @return a future containing the status per scanned package.
	 */
	@SuppressWarnings("unchecked")
	static CompletableFuture<Map<Package, PackageStatus>> checkPackages(PackageChecker checker,
			List<Package> packages) {

		Method checkPackages = CHECK_PACKAGES.getValue();
		if (checkPackages == null) {
			return CompletableFuture
					.failedFuture(new IllegalStateException("PackageChecker.checkPackages is not available"));
		}

		Function2<CoroutineScope, Continuation<? super Object>, Object> block = (scope, continuation) -> {
			try {
				// forward the builder's continuation; return value-or-sentinel unchanged
				return checkPackages.invoke(checker, packages, continuation);
			} catch (InvocationTargetException e) { // failure before first suspend
				Throwable cause = e.getCause();
				throw (cause instanceof RuntimeException runtimeException) ? runtimeException
						: new RuntimeException(cause);
			} catch (IllegalAccessException e) {
				throw new RuntimeException(e);
			}
		};

		CompletableFuture<Object> raw = FutureKt.future(checker, EmptyCoroutineContext.INSTANCE, CoroutineStart.DEFAULT,
				block);

		return (CompletableFuture<Map<Package, PackageStatus>>) (CompletableFuture<?>) raw;
	}

}
