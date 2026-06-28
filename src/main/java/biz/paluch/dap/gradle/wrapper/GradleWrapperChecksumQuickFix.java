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

package biz.paluch.dap.gradle.wrapper;

import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.codeInspection.LocalQuickFix;
import com.intellij.codeInspection.ProblemDescriptor;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;

/**
 * Quick-fix that inserts the precomputed Gradle wrapper checksum supplied at
 * construction next to the distribution URL property.
 *
 * @author Mark Paluch
 */
class GradleWrapperChecksumQuickFix implements LocalQuickFix {

	private final WrapperProperty property;

	private final String sha;

	public GradleWrapperChecksumQuickFix(WrapperProperty property, String sha) {
		this.property = property;
		this.sha = sha;
	}

	@Override
	public @NotNull String getFamilyName() {
		return MessageBundle.message("gradle.wrapper.checksum.intention-family");
	}

	@Override
	public @NotNull String getName() {
		return MessageBundle.message("wrapper.checksum.fix.add");
	}

	@Override
	public boolean startInWriteAction() {
		return false;
	}

	@Override
	public void applyFix(@NotNull Project project, @NotNull ProblemDescriptor descriptor) {

		if (!(descriptor.getStartElement() instanceof PropertyImpl urlProperty)
				|| !(urlProperty.getContainingFile() instanceof PropertiesFile properties)) {
			return;
		}

		String url = urlProperty.getUnescapedValue();
		if (!StringUtils.hasText(url)) {
			return;
		}

		WriteCommandAction.writeCommandAction(project, properties.getContainingFile())
				.withName(MessageBundle.message("wrapper.checksum.command"))
				.run(() -> addChecksum(properties, urlProperty, sha));
	}

	private void addChecksum(PropertiesFile properties, PropertyImpl urlProperty, String checksum) {

		if (properties.findPropertyByKey(property.shaKey()) == null) {
			properties.addPropertyAfter(property.shaKey(), checksum, urlProperty);
		}
	}

}
