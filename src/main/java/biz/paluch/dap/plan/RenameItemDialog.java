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

package biz.paluch.dap.plan;

import java.awt.Dimension;
import java.util.Collection;

import javax.swing.JComponent;

import biz.paluch.dap.util.MessageBundle;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.fileTypes.FileTypes;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.refactoring.ui.NameSuggestionsField;
import com.intellij.ui.components.JBCheckBox;
import com.intellij.ui.components.JBLabel;
import com.intellij.util.ui.DialogUtil;
import com.intellij.util.ui.FormBuilder;
import com.intellij.util.ui.JBUI;
import org.jspecify.annotations.Nullable;

/**
 * Collect a plan item name and optional persistence choices.
 * <p>The dialog reports choices to the handler. It does not change the plan or
 * descriptor.
 *
 * @author Mark Paluch
 */
class RenameItemDialog extends DialogWrapper {

	private static final int MIN_CONTENT_WIDTH = 520;

	private final String currentName;

	private final NameSuggestionsField nameField;

	private final JBCheckBox rememberName = new JBCheckBox(MessageBundle.message("plan.rename.remember"));

	private final JBCheckBox updateDependencyfile = new JBCheckBox(
			MessageBundle.message("plan.rename.dependencyfile"));

	public RenameItemDialog(Project project, String currentName, Collection<String> suggestions) {

		super(project, true);
		this.currentName = currentName;
		this.nameField = new NameSuggestionsField(suggestions.toArray(new String[0]), project,
				FileTypes.PLAIN_TEXT);
		setTitle(MessageBundle.message("plan.rename.title"));
		DialogUtil.registerMnemonic(rememberName);
		DialogUtil.registerMnemonic(updateDependencyfile);
		rememberName.setToolTipText(MessageBundle.message("plan.rename.remember.tooltip"));
		setUpdateDependencyfile(false, false);
		nameField.addDataChangedListener(this::validateButtons);
		init();
		validateButtons();
	}

	/**
	 * Trim and collapse line breaks so the name fits a tree row and commit subject.
	 * @return the normalized name, or {@literal null} if blank.
	 */
	static @Nullable String sanitize(@Nullable String name) {

		if (name == null) {
			return null;
		}

		String sanitized = name.replaceAll("[\\r\\n]+", " ").trim();
		return sanitized.isEmpty() ? null : sanitized;
	}

	/**
	 * Return the normalized entered name, or {@literal null} if blank.
	 */
	@Nullable
	public String getEnteredName() {
		return sanitize(nameField.getEnteredName());
	}

	public void setRememberName(boolean selected) {
		rememberName.setSelected(selected);
	}

	public boolean isRememberName() {
		return rememberName.isSelected();
	}

	/**
	 * Set descriptor-write availability and preselection. An unavailable choice
	 * remains unchecked.
	 */
	public void setUpdateDependencyfile(boolean available, boolean selected) {

		updateDependencyfile.setEnabled(available);
		updateDependencyfile.setSelected(available && selected);
		updateDependencyfile.setToolTipText(MessageBundle.message(
				available ? "plan.rename.dependencyfile.tooltip" : "plan.rename.dependencyfile.disabled"));
	}

	public boolean isUpdateDependencyfile() {
		return updateDependencyfile.isEnabled() && updateDependencyfile.isSelected();
	}

	@Override
	protected JComponent createCenterPanel() {

		JBLabel label = new JBLabel(MessageBundle.message("plan.rename.label", currentName));
		label.setLabelFor(nameField.getFocusableComponent());

		JComponent form = FormBuilder.createFormBuilder()
				.addLabeledComponent(label, nameField, true)
				.addVerticalGap(8)
				.addComponent(rememberName)
				.addComponent(updateDependencyfile)
				.getPanel();
		JComponent panel = JBUI.Panels.simplePanel().addToTop(form);
		Dimension preferred = panel.getPreferredSize();
		panel.setPreferredSize(new Dimension(Math.max(preferred.width, JBUI.scale(MIN_CONTENT_WIDTH)),
				preferred.height));
		return panel;
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent() {
		return nameField.getFocusableComponent();
	}

	private void validateButtons() {
		setOKActionEnabled(StringUtils.hasText(getEnteredName()));
	}

}
