package biz.paluch.dap.support;

import org.jspecify.annotations.Nullable;

/**
 * Interface for resolving properties against any underlying source.
 *
 * @author Mark Paluch
 */
public interface PropertyResolver {

	/**
	 * Determine whether the given property key is available for resolution &mdash;
	 * for example, if the value for the given key is not {@code null}.
	 */
	default boolean containsProperty(String key) {
		return getProperty(key) != null;
	}

	/**
	 * Resolve the property value associated with the given key, or {@code null} if
	 * the key cannot be resolved.
	 * @param key the property name to resolve.
	 */
	@Nullable
	String getProperty(String key);

}
