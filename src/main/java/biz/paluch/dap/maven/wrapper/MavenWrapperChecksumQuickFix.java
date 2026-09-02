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

import biz.paluch.dap.assistant.util.ChecksumDownloader;
import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.Property;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.SmartPointerManager;
import com.intellij.psi.SmartPsiElementPointer;

/**
 * Inspection quick fix that downloads the artifact referenced by a Maven
 * Wrapper URL and inserts its missing SHA-256 checksum property after the URL
 * property.
 *
 * <p>The download runs through {@link ChecksumDownloader} outside the write
 * action. The resulting write command inserts the checksum only while the URL
 * property is unchanged and the checksum property is still absent. Failures are
 * reported through a project notification. Cancellation and stale results make
 * no PSI change.
 *
 * @author Mark Paluch
 */
class MavenWrapperChecksumQuickFix implements LocalQuickFix {

	private final WrapperProperty property;

	MavenWrapperChecksumQuickFix(WrapperProperty property) {
		this.property = property;
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

		if (!(descriptor.getPsiElement() instanceof Property urlProperty)) {
			return;
		}

		String url = urlProperty.getUnescapedValue();
		if (url == null) {
			return;
		}

		SmartPsiElementPointer<Property> pointer = SmartPointerManager.createPointer(urlProperty);
		ChecksumDownloader.getInstance().computeSha(project, url).thenAccept((sha) -> {
			if (!project.isDisposed() && StringUtils.hasText(sha)) {
				insertChecksum(project, pointer, url, sha);
			}
		});
	}

	private void insertChecksum(Project project, SmartPsiElementPointer<Property> pointer, String expectedUrl,
			String sha) {

		WriteCommandAction.runWriteCommandAction(project, MessageBundle.message("wrapper.checksum.command"), null,
				() -> {
					Property current = pointer.getElement();
					if (current != null && expectedUrl.equals(current.getUnescapedValue())
							&& current.getContainingFile() instanceof PropertiesFile properties
							&& properties.findPropertyByKey(property.shaKey()) == null) {
						properties.addPropertyAfter(property.shaKey(), sha, current);
					}
				});
	}

}
