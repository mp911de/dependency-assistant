/*
 * Copyright 2026-present the original author or authors.
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

package biz.paluch.dap.maven.wrapper;

import java.io.IOException;

import biz.paluch.dap.assistant.Notifications;
import biz.paluch.dap.util.MessageBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;
import org.jspecify.annotations.Nullable;

/**
 * Inspection quick fix that downloads the artifact referenced by a Maven
 * Wrapper URL and inserts its missing SHA-256 checksum property after the URL
 * property.
 *
 * <p>The download runs outside the write action. The resulting write command
 * inserts the checksum only while the project remains open, the target URL is
 * unchanged, and the checksum property is still absent. Failures are reported
 * through a project notification. Cancellation and stale results make no PSI
 * change.
 *
 * @author Mark Paluch
 */
class MavenWrapperChecksumQuickFix implements LocalQuickFix {

	private final WrapperProperty property;

	private final @Nullable ChecksumComputer checksumComputer;

	MavenWrapperChecksumQuickFix(WrapperProperty property) {
		this(property, null);
	}

	MavenWrapperChecksumQuickFix(WrapperProperty property, ChecksumComputer checksumComputer) {
		this.property = property;
		this.checksumComputer = checksumComputer;
	}

	@Override
	public String getName() {
		return MessageBundle.message("wrapper.checksum.fix");
	}

	@Override
	public String getFamilyName() {
		return MessageBundle.message("maven.wrapper.checksum.intention-family");
	}

	@Override
	public boolean startInWriteAction() {
		return false;
	}

	@Override
	public boolean availableInBatchMode() {
		return false;
	}

	@Override
	public void applyFix(Project project, ProblemDescriptor descriptor) {

		if (!(descriptor.getPsiElement() instanceof Property urlProperty)
				|| !(urlProperty.getContainingFile() instanceof PropertiesFile properties)) {
			return;
		}

		String url = urlProperty.getUnescapedValue();
		if (url == null) {
			return;
		}

		SmartPsiElementPointer<Property> pointer = SmartPointerManager.createPointer(urlProperty);
		if (checksumComputer == null) {
			WrapperChecksumDownloader.downloadAndComputeSha(project, url,
					sha -> applyChecksum(project, pointer, url, sha),
					ex -> {
						if (!project.isDisposed()) {
							notifyError(project, url, ex);
						}
					}, () -> {
					});
			return;
		}

		try {
			applyChecksum(project, pointer, url, checksumComputer.compute(project, url));
		} catch (IOException ex) {
			notifyError(project, url, ex);
		}
	}

	private void applyChecksum(Project project, SmartPsiElementPointer<Property> pointer, String expectedUrl,
			String sha) {

		Property urlProperty = pointer.getElement();
		if (project.isDisposed() || urlProperty == null || !expectedUrl.equals(urlProperty.getUnescapedValue())
				|| sha == null || sha.isBlank()
				|| !(urlProperty.getContainingFile() instanceof PropertiesFile properties)) {
			return;
		}
		WriteCommandAction.runWriteCommandAction(project, MessageBundle.message("wrapper.checksum.command"), null,
				() -> {
					Property current = pointer.getElement();
					if (current != null && expectedUrl.equals(current.getUnescapedValue())
							&& properties.findPropertyByKey(property.shaKey()) == null) {
						properties.addPropertyAfter(property.shaKey(), sha, current);
					}
				});
	}

	private static void notifyError(Project project, String url, IOException ex) {
		Notifications.error(project, MessageBundle.message("wrapper.checksum.error.title"),
				MessageBundle.message("wrapper.checksum.error", url, Notifications.errorMessage(ex)));
	}

	@FunctionalInterface
	interface ChecksumComputer {

		String compute(Project project, String url) throws IOException;

	}

}
