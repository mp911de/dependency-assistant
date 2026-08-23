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
 * Slim name editor for renaming an Upgrade Plan item: a label, a
 * {@link NameSuggestionsField} seeded with names derived from the item, two
 * choices below the field, and the standard OK and Cancel buttons. OK stays
 * disabled while the entered name is blank; the accepted name is returned
 * {@link #sanitize(String) sanitized}.
 *
 * <p>The two choices are "remember name" (store the name as a personal name
 * hint for the item's constellation) and "update dependencyfile.json" (write
 * the name into the project's descriptor). The dialog only reports them through
 * {@link #isRememberName()} and {@link #isUpdateDependencyfile()}; the handler
 * preselects them from the persisted preferences and acts on the answers.
 *
 * <p>The dialog knows nothing about plan items, the service, the hint store, or
 * the metadata cache; it edits a name and two flags and nothing else.
 *
 * @author Mark Paluch
 */
class RenameItemDialog extends DialogWrapper {

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
	 * Normalize a typed or pasted name: trim surrounding whitespace and collapse
	 * line breaks so the result renders as one tree row and one commit subject.
	 *
	 * @param name the raw entered name.
	 * @return the sanitized name, or {@literal null} when nothing remains.
	 */
	static @Nullable String sanitize(@Nullable String name) {

		if (name == null) {
			return null;
		}

		String sanitized = name.replaceAll("[\\r\\n]+", " ").trim();
		return sanitized.isEmpty() ? null : sanitized;
	}

	/**
	 * Return the accepted name, sanitized, or {@literal null} when the field is
	 * blank. Meaningful after {@link #showAndGet()} returned {@literal true}.
	 */
	@Nullable
	public String getEnteredName() {
		return sanitize(nameField.getEnteredName());
	}

	/**
	 * Preselect the "remember name" choice.
	 */
	public void setRememberName(boolean selected) {
		rememberName.setSelected(selected);
	}

	public boolean isRememberName() {
		return rememberName.isSelected();
	}

	/**
	 * Enable and preselect the "update dependencyfile.json" choice. A disabled
	 * choice always shows unchecked and explains itself through its tooltip.
	 *
	 * @param available whether a descriptor exists to write to.
	 * @param selected the preselection, honoured only when {@code available}.
	 */
	public void setUpdateDependencyfile(boolean available, boolean selected) {

		updateDependencyfile.setEnabled(available);
		updateDependencyfile.setSelected(available && selected);
		updateDependencyfile.setToolTipText(MessageBundle.message(
				available ? "plan.rename.dependencyfile.tooltip" : "plan.rename.dependencyfile.disabled"));
	}

	/**
	 * Whether the name should be written to {@code dependencyfile.json}; always
	 * {@literal false} when the choice was not available.
	 */
	public boolean isUpdateDependencyfile() {
		return updateDependencyfile.isEnabled() && updateDependencyfile.isSelected();
	}

	/**
	 * Form layout: the name field grows horizontally with the dialog but keeps its
	 * preferred height; the choices sit below it.
	 */
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
		return JBUI.Panels.simplePanel().addToTop(form);
	}

	@Override
	public @Nullable JComponent getPreferredFocusedComponent() {
		return nameField.getFocusableComponent();
	}

	private void validateButtons() {
		setOKActionEnabled(StringUtils.hasText(getEnteredName()));
	}

}
