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
 * Mirror of {@code IntelliJVirtualThreads}.
 */
public final class VirtualThreads {

	private VirtualThreads() {
	}

	/**
	 * By default, virtual threads run on top of Fork-Join Pool. We use coroutine
	 * scheduler as the main scheduler of IntelliJ Platform, so we need to replace
	 * FJP in default constructors of virtual thread factories.
	 * <p>Until JDK gets an API for setting custom executors, we use reflection.
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
	 * Returns a virtual thread builder.
	 * <p>This method is preferable to {@link Thread#ofVirtual()}, as it allows
	 * IntelliJ Platform to perform modifications to virtual threads.
	 */
	public static Thread.Builder ofVirtual() {
		return getVirtualBuilder();
	}

}
