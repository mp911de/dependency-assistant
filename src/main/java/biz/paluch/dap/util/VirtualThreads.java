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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Constructor;
import java.util.concurrent.Executor;

import kotlinx.coroutines.Dispatchers;
import kotlinx.coroutines.ExecutorsKt;

/**
 * Creates virtual-thread builders backed by IntelliJ's coroutine scheduler when
 * the running JDK permits that integration.
 *
 * <p>The integration uses a non-public JDK constructor. If reflective access is
 * unavailable or invocation fails, builders fall back to
 * {@link Thread#ofVirtual()}.
 */
public final class VirtualThreads {

	private VirtualThreads() {
	}

	/*
	 * Attempt to install IntelliJ's coroutine scheduler through the JDK's internal
	 * virtual-thread builder constructor. Public JDK builders do not accept an
	 * executor.
	 */
	private static final MethodHandle virtualThreadBuilderConstructor;

	static {
		MethodHandle handle;
		try {
			Class<?> clazz = Class.forName("java.lang.ThreadBuilders$VirtualThreadBuilder");
			Constructor<?> ctor = clazz.getDeclaredConstructor(Executor.class);
			ctor.setAccessible(true);
			handle = MethodHandles.lookup().unreflectConstructor(ctor);
		} catch (Throwable e) {
			handle = null;
		}
		virtualThreadBuilderConstructor = handle;
	}

	private static Thread.Builder getVirtualBuilder() {
		if (virtualThreadBuilderConstructor == null) {
			return Thread.ofVirtual();
		}
		try {
			Executor executor = ExecutorsKt.asExecutor(Dispatchers.getDefault());
			return (Thread.Builder) virtualThreadBuilderConstructor.invoke(executor);
		} catch (Throwable e) {
			return Thread.ofVirtual();
		}
	}

	/**
	 * Return a virtual-thread builder using the IntelliJ coroutine scheduler when
	 * available, otherwise the JDK default scheduler.
	 *
	 * @return a new virtual-thread builder.
	 */
	public static Thread.Builder ofVirtual() {
		return getVirtualBuilder();
	}

}
