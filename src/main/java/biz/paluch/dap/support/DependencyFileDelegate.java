package biz.paluch.dap.support;

import java.util.function.Function;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Per-file context for inspecting the dependencies declared in a single build
 * file.
 *
 * <p>Bundles the {@link Project} and the target {@link VirtualFile}.
 *
 * <p>The delegate holds no mutable state; the bound file may become invalid
 * over the delegate's lifetime, in which case {@link #collectDependencies}
 * yields an empty result.
 *
 * @author Mark Paluch
 */
public class DependencyFileDelegate {

	private final Project project;

	private final VirtualFile file;

	private DependencyFileDelegate(Project project, VirtualFile file) {
		this.project = project;
		this.file = file;
	}

	/**
	 * Create a delegate bound to the given project and build file.
	 *
	 * @param project the owning project.
	 * @param file the build file to inspect.
	 * @return a new delegate instance; guaranteed to be not {@literal null}.
	 */
	public static DependencyFileDelegate of(Project project, VirtualFile file) {
		return new DependencyFileDelegate(project, file);
	}

	public Project getProject() {
		return this.project;
	}

	public VirtualFile getFile() {
		return this.file;
	}

	/**
	 * Collect the dependencies declared in the bound file.
	 *
	 * <p>Resolves the {@link PsiFile} for the bound file and applies
	 * {@code collectorFunction} to it. When the file is invalid or has no PSI, an
	 * empty {@link DependencyCollector} is returned and the function is not
	 * invoked. Must be called inside a read action.
	 *
	 * @param collectorFunction the format-specific collector applied to the
	 * resolved {@link PsiFile}.
	 * @return the collected dependencies, or an empty {@link DependencyCollector}
	 * when the file cannot be resolved; guaranteed to be not {@literal null}.
	 */
	public DependencyCollector collectDependencies(Function<PsiFile, DependencyCollector> collectorFunction) {
		PsiFile psiFile = BetterPsiManager.getInstance(this.project).findFile(this.file);
		return psiFile != null ? collectorFunction.apply(psiFile) : new DependencyCollector();
	}

	@Override
	public String toString() {
		return file.toString();
	}

}
