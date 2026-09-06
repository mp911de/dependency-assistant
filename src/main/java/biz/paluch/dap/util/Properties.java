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

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Java properties in declaration order.
 * <p>PSI-backed views read the file when consumed and require a read action.
 *
 * @author Mark Paluch
 */
public interface Properties<P> {

	void forEach(Consumer<P> consumer);

	/**
	 * Return a mapped view that omits {@literal null} mapping results.
	 */
	<T> Properties<T> filterMap(Function<? super P, ? extends @Nullable T> function);

	List<P> toList();

	/**
	 * Return a PSI-backed property view for the given file.
	 */
	public static Properties<PropertyImpl> from(PsiFile file) {
		return new PropertyFile(file);
	}

	/**
	 * Return a PSI-backed property view for the given properties file.
	 */
	public static Properties<PropertyImpl> from(PropertiesFile file) {
		return PropertyFile.from(file);
	}

}
