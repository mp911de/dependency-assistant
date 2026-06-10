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
 * Validity-safe facade over the platform {@link PsiManager}.
 *
 * <p>Every lookup guards its {@link VirtualFile} argument with
 * {@link #isValid(VirtualFile)} before delegating to
 * {@link PsiManager#findFile(VirtualFile)}, so a stale, deleted, or otherwise
 * invalid file resolves to an absent result rather than throwing or returning
 * torn state. The facade exposes the same resolution in several shapes
 * ({@link #doWithFile} callback, {@link #findFile} nullable return,
 * {@link #stream} for bulk resolution, and {@link #optional}) so callers can
 * pick the form that fits their control flow.
 *
 * <p>Resolving PSI reads the project model, so the instance methods must be
 * invoked inside a read action. Instances are cheap, stateless beyond their
 * delegate, and may be created on demand via {@link #getInstance(Project)}.
 *
 * @author Mark Paluch
 */
public class BetterPsiManager {

	private final PsiManager delegate;

	private BetterPsiManager(PsiManager delegate) {
		this.delegate = delegate;
	}

	/**
	 * Create a facade backed by the {@link PsiManager} of the given project.
	 *
	 * @param project the project whose {@link PsiManager} backs the facade; must
	 * not be {@literal null}.
	 * @return a new facade instance; guaranteed to be not {@literal null}.
	 */
	public static BetterPsiManager getInstance(Project project) {
		return getInstance(PsiManager.getInstance(project));
	}

	/**
	 * Create a facade wrapping the given {@link PsiManager}.
	 *
	 * @param delegate the {@link PsiManager} to delegate lookups to; must not be
	 * {@literal null}.
	 * @return a new facade instance; guaranteed to be not {@literal null}.
	 */
	public static BetterPsiManager getInstance(PsiManager delegate) {
		return new BetterPsiManager(delegate);
	}

	/**
	 * Resolve the {@link PsiFile} for the given file and pass it to
	 * {@code consumer}.
	 *
	 * <p>The consumer is invoked only when the file is valid and resolves to a
	 * {@link PsiFile}; otherwise the call is a no-op. Must be called inside a read
	 * action.
	 *
	 * @param file the file to resolve; can be {@literal null} or invalid, in which
	 * case nothing happens.
	 * @param consumer the action to run with the resolved {@link PsiFile}; must not
	 * be {@literal null}.
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
	 * Resolve the {@link PsiFile} for the given file.
	 *
	 * <p>Must be called inside a read action.
	 *
	 * @param file the file to resolve; can be {@literal null} or invalid.
	 * @return the resolved {@link PsiFile}, or {@literal null} when the file is
	 * invalid or has no PSI.
	 */
	public @Nullable PsiFile findFile(VirtualFile file) {
		return isValid(file) ? delegate.findFile(file) : null;
	}

	/**
	 * Resolve the {@link PsiFile} for each given file, skipping invalid and
	 * unresolvable entries.
	 *
	 * <p>Must be called inside a read action. The returned stream is lazy;
	 * resolution happens as the stream is consumed.
	 *
	 * @param files the files to resolve; must not be {@literal null}, individual
	 * entries may be invalid.
	 * @return a stream of the resolvable {@link PsiFile PsiFiles}, in iteration
	 * order of {@code files}.
	 */
	public Stream<PsiFile> stream(Collection<VirtualFile> files) {
		return files.stream().filter(BetterPsiManager::isValid).flatMap(it -> {
			PsiFile psiFile = delegate.findFile(it);
			return psiFile != null ? Stream.of(psiFile) : Stream.empty();
		});
	}

	/**
	 * Resolve the {@link PsiFile} for the given file as an {@link Optional}.
	 *
	 * <p>Must be called inside a read action.
	 *
	 * @param file the file to resolve; can be {@literal null} or invalid.
	 * @return an {@link Optional} holding the resolved {@link PsiFile}, or empty
	 * when the file is invalid or has no PSI.
	 */
	public Optional<PsiFile> optional(VirtualFile file) {
		return Optional.ofNullable(findFile(file));
	}

	/**
	 * Test whether the given file is present and usable for PSI resolution.
	 *
	 * @param file the file to test; can be {@literal null}.
	 * @return {@literal true} if the file is non-{@literal null}, exists, and is
	 * valid; {@literal false} otherwise.
	 */
	@Contract("null -> false")
	public static boolean isValid(@Nullable VirtualFile file) {
		return file != null && file.exists() && file.isValid();
	}

	/**
	 * Test whether the given file is missing or unusable for PSI resolution.
	 *
	 * @param file the file to test; can be {@literal null}.
	 * @return {@literal true} if the file is {@literal null}, does not exist, or is
	 * invalid; {@literal false} otherwise.
	 * @see #isValid(VirtualFile)
	 */
	@Contract("null -> true")
	public static boolean isInvalid(@Nullable VirtualFile file) {
		return !isValid(file);
	}

}
