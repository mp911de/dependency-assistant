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

package biz.paluch.dap.extension;

import com.intellij.openapi.Disposable;
import com.intellij.openapi.util.Disposer;
import com.intellij.testFramework.common.ThreadLeakTracker;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;

/**
 * Registers the JDK's virtual-thread infrastructure threads with IntelliJ's
 * {@link ThreadLeakTracker}. The JDK starts them on first virtual-thread use
 * and keeps them for the JVM's lifetime: the I/O pollers ({@code MasterPoller}
 * on Linux epoll, {@code Read-Poller} and {@code Write-Poller} on macOS kqueue)
 * plus the unparker and unblocker. IntelliJ's leak tracker runs after every
 * test through auto-detected extensions, so this registration applies globally
 * via {@code META-INF/services}.
 *
 * @author Mark Paluch
 */
public class JdkThreadsExtension implements BeforeAllCallback {

	private static final Disposable JVM_LIFETIME = Disposer.newDisposable("JDK virtual-thread infrastructure");

	private static volatile boolean registered;

	@Override
	public void beforeAll(ExtensionContext context) {

		if (registered) {
			return;
		}
		registered = true;
		ThreadLeakTracker.longRunningThreadCreated(JVM_LIFETIME, "MasterPoller", "Read-Poller", "Write-Poller",
				"VirtualThread-unparker", "VirtualThread-unblocker");
	}

}
