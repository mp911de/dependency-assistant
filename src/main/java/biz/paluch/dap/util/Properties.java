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

package biz.paluch.dap.util;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.psi.PsiFile;
import org.jspecify.annotations.Nullable;

/**
 * Iterable view over Java properties in a PSI file.
 *
 * <p>
 * This abstraction keeps callers independent from the concrete
 * {@link PropertiesFile} traversal while retaining declaration order. Mapping
 * functions may return {@literal null} to filter out unsupported properties.
 *
 * @param <P> the property item type exposed by this view.
 * @author Mark Paluch
 */
public interface Properties<P> {

	/**
	 * Invoke the consumer for each property item in declaration order.
	 * @param consumer the consumer to invoke.
	 */
	void forEach(Consumer<P> consumer);

	/**
	 * Return a mapped view that omits {@literal null} mapping results.
	 * @param function the mapping function to apply.
	 * @return a property view containing only non-null mapped values.
	 * @param <T> the mapped item type.
	 */
	<T> Properties<T> filterMap(Function<? super P, ? extends @Nullable T> function);

	/**
	 * Return the property items as a list.
	 * @return the property items in declaration order.
	 */
	List<P> toList();

	/**
	 * Return a PSI-backed property view for the given file.
	 * @param file the PSI file to adapt.
	 * @return a property view over the file.
	 */
	public static Properties<PropertyImpl> from(PsiFile file) {
		return new PropertyFile(file);
	}

	/**
	 * Return a PSI-backed property view for the given properties file.
	 * @param file the properties file to adapt.
	 * @return a property view over the file.
	 */
	public static Properties<PropertyImpl> from(PropertiesFile file) {
		return PropertyFile.from(file);
	}

}
