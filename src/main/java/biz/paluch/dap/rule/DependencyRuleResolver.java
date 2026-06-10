package biz.paluch.dap.rule;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.Versioned;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jspecify.annotations.Nullable;

/**
 * Resolver for {@link DependencyRule}s.
 *
 * @author Mark Paluch
 */
public interface DependencyRuleResolver {

	/**
	 * Resolve an effective {@link DependencyRule} for the given {@link ArtifactId}
	 * and {@link Versioned} project version.
	 */
	DependencyRule resolve(ArtifactId artifactId, Project project, @Nullable VirtualFile file,
			Versioned projectVersion);

}
