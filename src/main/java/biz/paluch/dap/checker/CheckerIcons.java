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

import javax.swing.Icon;

import com.intellij.openapi.util.IconLoader;

/**
 * Package checker icons.
 *
 * @author Mark Paluch
 */
public class CheckerIcons {

	public static final Icon HIGH = load("/META-INF/icons/checker/highAllTree.svg");

	public static final Icon HIGH_OUTLINE = load("/META-INF/icons/checker/highAllTreeOutline.svg");

	public static final Icon LOW = load("/META-INF/icons/checker/lowAllTree.svg");

	public static final Icon LOW_OUTLINE = load("/META-INF/icons/checker/lowAllTreeOutline.svg");

	public static final Icon MEDIUM = load("/META-INF/icons/checker/mediumAllTree.svg");

	public static final Icon MEDIUM_OUTLINE = load("/META-INF/icons/checker/mediumAllTreeOutline.svg");

	public static final Icon SAFE = load("/META-INF/icons/checker/safeAllTree.svg");

	public static final Icon SAFE_OUTLINE = load("/META-INF/icons/checker/safeAllTreeOutline.svg");

	public static final Icon UNKNOWN = load("/META-INF/icons/checker/uncheckedAllTree.svg");

	public static final Icon UNKNOWN_OUTLINE = load("/META-INF/icons/checker/uncheckedAllTreeOutline.svg");

	private static Icon load(String path) {
		return IconLoader.getIcon(path, CheckerIcons.class.getClassLoader());
	}

}
