package biz.paluch.dap.gradle;

import biz.paluch.dap.support.ArtifactReference;

/**
 * Strategy for resolving an {@link ArtifactReference} from a Gradle PSI
 * element.
 *
 * @author Mark Paluch
 * @param <T> the supported PSI element type.
 */
interface ArtifactReferenceLocator<T> {

	/**
	 * Resolve the artifact reference from the given Gradle PSI element.
	 *
	 * @param element the PSI element to inspect.
	 * @return the artifact reference, or an unresolved reference if no supported
	 * declaration can be derived.
	 */
	ArtifactReference locate(T element);

}
