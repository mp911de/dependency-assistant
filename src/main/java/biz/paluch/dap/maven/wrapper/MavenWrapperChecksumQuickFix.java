/*
 * Copyright 2026 the original author or authors.
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
import biz.paluch.dap.support.MessageBundle;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.project.Project;

/**
 * Quick-fix that computes and inserts a missing Maven wrapper checksum.
 *
 * @author Mark Paluch
 */
class MavenWrapperChecksumQuickFix implements LocalQuickFix {

	private final WrapperProperty property;

	private final ChecksumComputer checksumComputer;

	MavenWrapperChecksumQuickFix(WrapperProperty property) {
		this(property, WrapperChecksumDownloader::downloadAndComputeSha);
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
		return MessageBundle.message("intention.family.name");
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

		if (!(descriptor.getPsiElement() instanceof PropertyImpl urlProperty)
				|| !(urlProperty.getContainingFile() instanceof PropertiesFile properties)) {
			return;
		}

		String url = urlProperty.getUnescapedValue();
		if (url == null) {
			return;
		}

		String sha;
		try {
			sha = checksumComputer.compute(project, url);
		} catch (ProcessCanceledException | IOException ex) {
			Notifications.error(project, MessageBundle.message("wrapper.checksum.error.title"),
					MessageBundle.message("wrapper.checksum.error", url, Notifications.errorMessage(ex)));
			return;
		}

		if (sha == null || sha.isBlank()) {
			return;
		}

		WriteCommandAction.runWriteCommandAction(project, MessageBundle.message("wrapper.checksum.command"), null,
				() -> {
					if (properties.findPropertyByKey(property.shaKey()) == null) {
						properties.addPropertyAfter(property.shaKey(), sha, urlProperty);
					}
				});
	}

	@FunctionalInterface
	interface ChecksumComputer {

		String compute(Project project, String url) throws IOException;

	}

}
