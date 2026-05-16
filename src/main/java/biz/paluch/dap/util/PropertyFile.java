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

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import com.intellij.util.containers.JBIterable;
import org.jetbrains.annotations.Nullable;

/**
 * PSI-backed {@link Properties} view for Java {@code .properties} files.
 *
 * <p>The view traverses the PSI tree to include nested property elements in the
 * order reported by IntelliJ's syntax traverser. It is useful for parsers that
 * want filtering and mapping without exposing traversal details.
 *
 * @author Mark Paluch
 */
public class PropertyFile implements Properties<PropertyImpl> {

	private final PsiFile psiFile;

	PropertyFile(PsiFile psiFile) {
		this.psiFile = psiFile;
	}

	/**
	 * Return a PSI-backed property view for the given properties file.
	 * @param file the properties file to adapt.
	 * @return a property view over the file.
	 */
	public static PropertyFile from(PropertiesFile file) {
		return new PropertyFile((PsiFile) file);
	}

	/**
	 * Invoke the consumer for each property in declaration order.
	 * @param consumer the consumer to invoke.
	 */
	@Override
	public void forEach(Consumer<PropertyImpl> consumer) {
		traverse()
				.forEach(consumer);
	}

	/**
	 * Return a mapped view that omits {@code null} mapping results.
	 * @param function the mapping function to apply.
	 * @return a property view containing only non-null mapped values.
	 * @param <T> the mapped item type.
	 */
	@Override
	public <T> Properties<T> filterMap(Function<? super PropertyImpl, ? extends @Nullable T> function) {

		return new Properties<>() {

			@Override
			public void forEach(Consumer<T> consumer) {
				traverse()
						.filterMap(function::apply)
						.forEach(consumer);
			}

			@Override
			public <T1> Properties<T1> filterMap(Function<? super T, ? extends @Nullable T1> function) {
				return PropertyFile.filterMap(this, function);
			}

			@Override
			public List<T> toList() {
				return PropertyFile.toList(this);
			}

		};
	}

	/**
	 * Return the properties as a list.
	 * @return the properties in declaration order.
	 */
	@Override
	public List<PropertyImpl> toList() {
		return toList(this);
	}

	private JBIterable<PropertyImpl> traverse() {
		return SyntaxTraverser.psiTraverser(psiFile)
				.filter(PropertyImpl.class);
	}

	static <P, T> Properties<T> filterMap(Properties<P> properties,
			Function<? super P, ? extends @Nullable T> function) {
		List<T> result = new ArrayList<>();
		properties.forEach(it -> {
			T mapped = function.apply(it);
			if (mapped != null) {
				result.add(mapped);
			}
		});
		return new MappedProperties<>(result);
	}

	static <T> List<T> toList(Properties<T> properties) {
		List<T> result = new ArrayList<>();
		properties.forEach(result::add);
		return result;
	}

	record MappedProperties<P>(List<P> properties) implements Properties<P> {

		@Override
		public void forEach(Consumer<P> consumer) {
			properties.forEach(consumer);
		}

		@Override
		public <T> Properties<T> filterMap(Function<? super P, ? extends @Nullable T> function) {
			return PropertyFile.filterMap(this, function);
		}

		@Override
		public List<P> toList() {
			return properties;
		}

	}

}
