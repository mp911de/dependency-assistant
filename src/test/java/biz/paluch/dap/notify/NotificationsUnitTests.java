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

package biz.paluch.dap.notify;

import java.io.IOException;
import java.io.UncheckedIOException;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.*;

/**
 * Unit tests for {@link Notifications} and {@link NotificationActions}.
 *
 * @author Mark Paluch
 */
class NotificationsUnitTests {

	@Test
	void errorMessageUnwrapsWrappersWithoutOwnMessage() {

		IOException cause = new IOException("disk full");

		assertThat(Notifications.errorMessage(new UncheckedIOException(cause))).isEqualTo("disk full");
		assertThat(Notifications.errorMessage(new RuntimeException(cause))).isEqualTo("disk full");
		assertThat(Notifications.errorMessage(new IllegalStateException("broken", cause))).isEqualTo("broken");
		assertThat(Notifications.errorMessage(new IllegalStateException())).isEqualTo("IllegalStateException");
	}

}
