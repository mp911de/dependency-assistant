package biz.paluch.dap.support;

import java.util.function.Function;

import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.PackageSystem;
import biz.paluch.dap.util.BetterPsiManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

/**
 * Dependency collection bound to one build file.
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
	 * Collect dependencies. Callers must hold a read action. An invalid file or
	 * missing PSI returns an empty collector without invoking the supplied
	 * function.
	 *
	 * @param packageSystem the package system for the empty collector.
	 */
	public DependencyCollector collectDependencies(PackageSystem packageSystem,
			Function<PsiFile, DependencyCollector> collectorFunction) {
		PsiFile psiFile = BetterPsiManager.getInstance(this.project).findFile(this.file);
		return psiFile != null ? collectorFunction.apply(psiFile) : new DependencyCollector(packageSystem);
	}

	@Override
	public String toString() {
		return file.toString();
	}

}
