package biz.paluch.dap.util;

import java.util.Collection;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.stream.Stream;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jspecify.annotations.Nullable;

import org.springframework.lang.Contract;

/**
 * Resolve virtual files to PSI, treating invalid files as absent.
 * <p>Perform lookups in a read action. Streams must also be consumed within a
 * read action because resolution is lazy.
 *
 * @author Mark Paluch
 */
public class BetterPsiManager {

	private final PsiManager delegate;

	private BetterPsiManager(PsiManager delegate) {
		this.delegate = delegate;
	}

	public static BetterPsiManager getInstance(Project project) {
		return getInstance(PsiManager.getInstance(project));
	}

	public static BetterPsiManager getInstance(PsiManager delegate) {
		return new BetterPsiManager(delegate);
	}

	/**
	 * Invoke the consumer if the file is valid and has PSI.
	 */
	public void doWithFile(VirtualFile file, Consumer<PsiFile> consumer) {
		if (isValid(file)) {
			PsiFile psiFile = delegate.findFile(file);

			if (psiFile != null) {
				consumer.accept(psiFile);
			}
		}
	}

	/**
	 * Return the file's PSI, or {@literal null} if the file is invalid or has no
	 * PSI.
	 */
	public @Nullable PsiFile findFile(VirtualFile file) {
		return isValid(file) ? delegate.findFile(file) : null;
	}

	/**
	 * Resolve files lazily in iteration order, skipping invalid files and files
	 * without PSI.
	 */
	public Stream<PsiFile> stream(Collection<VirtualFile> files) {
		return files.stream().filter(BetterPsiManager::isValid).flatMap(it -> {
			PsiFile psiFile = delegate.findFile(it);
			return psiFile != null ? Stream.of(psiFile) : Stream.empty();
		});
	}

	public Optional<PsiFile> optional(VirtualFile file) {
		return Optional.ofNullable(findFile(file));
	}

	/**
	 * Return whether the file is present, exists, and is valid.
	 */
	@Contract("null -> false")
	public static boolean isValid(@Nullable VirtualFile file) {
		return file != null && file.exists() && file.isValid();
	}

	@Contract("null -> true")
	public static boolean isInvalid(@Nullable VirtualFile file) {
		return !isValid(file);
	}

}
