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
package biz.paluch.mavenupdater;

import com.intellij.DynamicBundle;

import org.jetbrains.annotations.Nls;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.PropertyKey;

import java.util.function.Supplier;

/**
 * Message bundle for plugin strings.
 */
public class MessageBundle {

	private static final @NonNls String BUNDLE = "messages.MessageBundle";

	private static final DynamicBundle INSTANCE = new DynamicBundle(MessageBundle.class, BUNDLE);

	private MessageBundle() {
	}

	@Nls
	public static String message(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
		return INSTANCE.getMessage(key, params);
	}

	public static Supplier<@Nls String> lazyMessage(@PropertyKey(resourceBundle = BUNDLE) String key, Object... params) {
		return INSTANCE.getLazyMessage(key, params);
	}

}
