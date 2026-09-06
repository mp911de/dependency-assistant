package biz.paluch.dap.gradle;

import biz.paluch.dap.support.ArtifactReference;

/**
 * Resolve artifact references from Gradle PSI.
 *
 * @author Mark Paluch
 */
interface ArtifactReferenceLocator<T> {

	/**
	 * Return the artifact reference, or an unresolved reference for unsupported
	 * declarations.
	 */
	ArtifactReference locate(T element);

}
