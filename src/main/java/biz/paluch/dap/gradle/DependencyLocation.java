package biz.paluch.dap.gradle;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.gradle.GradleDependency.PropertyManagedDependency;
import com.intellij.psi.PsiElement;

/**
 * Location of a version string in a dependency declaration.
 */
record DependencyLocation(PsiElement element, GradleDependency dependency) {

	public ArtifactId artifactId() {
		return dependency.getId();
	}

	public boolean isPropertyReference() {
		return dependency instanceof PropertyManagedDependency;
	}

}
