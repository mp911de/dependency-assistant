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

package biz.paluch.dap.rule;

import java.util.HashSet;
import java.util.Set;

import biz.paluch.dap.state.StateService;
import com.intellij.codeInsight.completion.CompletionContributor;
import com.intellij.codeInsight.completion.CompletionParameters;
import com.intellij.codeInsight.completion.CompletionProvider;
import com.intellij.codeInsight.completion.CompletionResultSet;
import com.intellij.codeInsight.completion.CompletionType;
import com.intellij.codeInsight.completion.InsertHandler;
import com.intellij.codeInsight.completion.InsertionContext;
import com.intellij.codeInsight.lookup.LookupElement;
import com.intellij.codeInsight.lookup.LookupElementBuilder;
import com.intellij.codeInsight.template.Template;
import com.intellij.codeInsight.template.TemplateManager;
import com.intellij.codeInsight.template.impl.TextExpression;
import com.intellij.icons.AllIcons;
import com.intellij.json.psi.JsonFile;
import com.intellij.json.psi.JsonObject;
import com.intellij.json.psi.JsonProperty;
import com.intellij.json.psi.JsonStringLiteral;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.DumbAware;
import com.intellij.openapi.project.Project;
import com.intellij.patterns.ElementPattern;
import com.intellij.patterns.PlatformPatterns;
import com.intellij.patterns.PsiElementPattern;
import com.intellij.patterns.PsiFilePattern;
import com.intellij.psi.PsiDocumentManager;
import com.intellij.psi.PsiElement;
import com.intellij.psi.util.PsiTreeUtil;
import com.intellij.util.ProcessingContext;
import org.jspecify.annotations.Nullable;

/**
 * Completion contributor for artifact pattern keys inside
 * {@code dependencyfile.json} {@code artifacts} sections.
 *
 * <p>Suggests artifact patterns from the {@link StateService} cache when the
 * caret is at the key position of a property inside a top-level or branch-level
 * {@code artifacts} object. Accepting a suggestion starts a live template that
 * inserts the rule object with a {@code name} tab stop (and a
 * {@code generation} tab stop for branch rules). The template reformats to the
 * file's JSON code style. Completion also fires from a whitespace position
 * between properties; in that case the template re-quotes the inserted key.
 *
 * @author Mark Paluch
 */
public class DependencyfileCompletionContributor extends CompletionContributor implements DumbAware {

	public static final PsiFilePattern.Capture<JsonFile> DEPENDENCYFILE = PlatformPatterns
			.psiFile(JsonFile.class).withName(DependencyfileService.FILE_NAME);

	public static final PsiElementPattern.Capture<JsonObject> ARTIFACTS = PlatformPatterns
			.psiElement(JsonObject.class)
			.withParent(PlatformPatterns.psiElement(JsonProperty.class)
					.withName("artifacts"));

	public static final PsiElementPattern.Capture<JsonStringLiteral> PATTERN = PlatformPatterns
			.psiElement(JsonStringLiteral.class)
			.withParent(PlatformPatterns.psiElement(JsonProperty.class)
					.withParent(ARTIFACTS));

	private static final ElementPattern<PsiElement> IN_ARTIFACTS = PlatformPatterns.psiElement()
			.inside(DEPENDENCYFILE)
			.inside(ARTIFACTS);

	private static final InsertHandler<LookupElement> TOP_LEVEL_INSERT = new ArtifactRuleInsertHandler(false);

	private static final InsertHandler<LookupElement> BRANCH_INSERT = new ArtifactRuleInsertHandler(true);

	public DependencyfileCompletionContributor() {
		extend(CompletionType.BASIC, IN_ARTIFACTS, new ArtifactPatternProvider());
	}

	private static boolean isBranchArtifacts(JsonObject artifactsObject) {

		if (!(artifactsObject.getParent() instanceof JsonProperty artifactsProperty)) {
			return false;
		}
		if (!(artifactsProperty.getParent() instanceof JsonObject branchObject)) {
			return false;
		}
		if (!(branchObject.getParent() instanceof JsonProperty branchNameProperty)) {
			return false;
		}
		if (!(branchNameProperty.getParent() instanceof JsonObject branchesValue)) {
			return false;
		}
		return branchesValue.getParent() instanceof JsonProperty branchesProperty
				&& "branches".equals(branchesProperty.getName());
	}

	private static @Nullable JsonProperty findValuelessProperty(InsertionContext context) {

		PsiDocumentManager.getInstance(context.getProject()).commitDocument(context.getDocument());
		PsiElement element = context.getFile().findElementAt(context.getTailOffset() - 1);
		JsonProperty property = PsiTreeUtil.getParentOfType(element, JsonProperty.class, false);
		return (property != null && property.getValue() == null) ? property : null;
	}

	private static class ArtifactPatternProvider extends CompletionProvider<CompletionParameters> {

		@Override
		protected void addCompletions(CompletionParameters parameters, ProcessingContext context,
				CompletionResultSet result) {

			PsiElement position = parameters.getPosition();
			JsonObject artifactsObject;
			JsonProperty currentKey = null;

			if (position.getParent() instanceof JsonStringLiteral literal) {
				// Caret is inside an existing quoted key string.
				if (!(literal.getParent() instanceof JsonProperty property)) {
					return;
				}
				if (property.getNameElement() != literal) {
					return; // inside a value string, not a key
				}
				if (!(property.getParent() instanceof JsonObject ao)) {
					return;
				}
				artifactsObject = ao;
				currentKey = property;
			} else {
				// Caret is in whitespace or an error element between properties.
				// The dummy identifier was inserted without surrounding quotes, so there is
				// no JsonStringLiteral ancestor, so find the enclosing artifacts object
				// directly.
				artifactsObject = PsiTreeUtil.getParentOfType(position, JsonObject.class, false);
				if (artifactsObject == null) {
					return;
				}
			}
			if (!ARTIFACTS.accepts(artifactsObject)) {
				return;
			}

			// Seed the dedup set with declared keys so already-declared patterns are not
			// suggested.
			Set<String> seen = new HashSet<>();
			for (JsonProperty sibling : artifactsObject.getPropertyList()) {
				if (sibling != currentKey) {
					seen.add(sibling.getName());
				}
			}

			InsertHandler<LookupElement> handler = isBranchArtifacts(artifactsObject) ? BRANCH_INSERT
					: TOP_LEVEL_INSERT;

			StateService.getInstance(position.getProject()).doWithDependencies(dependency -> {
				String pattern = ArtifactPattern.keyFor(dependency.getArtifactId());
				if (seen.add(pattern)) {
					result.addElement(LookupElementBuilder.create(pattern).withInsertHandler(handler)
							.withIcon(AllIcons.Nodes.Artifact));
				}
			});
		}

	}

	@Override
	public boolean invokeAutoPopup(PsiElement position, char typeChar) {

		StateService state = StateService.getInstance(position.getProject());
		if (!state.hasDependenciesOrReleases()) {
			return false;
		}

		if ((typeChar == ',' || typeChar == '"') && IN_ARTIFACTS.accepts(position)) {
			return true;
		}

		return super.invokeAutoPopup(position, typeChar);
	}

	/**
	 * Starts the template at the artifact pattern key, quoting the key when
	 * completion was accepted from a whitespace position.
	 *
	 * <p>On an existing quoted key the template is appended right after it. From a
	 * whitespace position the bare, unquoted key the platform inserted is removed
	 * and the quoted key becomes the template's leading segment, so the whole
	 * inserted region is reformatted as one unit and the live-template segments
	 * stay in sync.
	 */
	private static void prepareKeyPrefix(InsertionContext context, LookupElement item, Template template) {

		JsonProperty property = findValuelessProperty(context);
		int startOffset;
		if (property != null) {
			startOffset = property.getNameElement().getTextRange().getEndOffset();
		} else {
			startOffset = context.getStartOffset();
			context.getDocument().deleteString(startOffset, context.getTailOffset());
			template.addTextSegment("\"" + item.getLookupString() + "\"");
		}

		context.getEditor().getCaretModel().moveToOffset(startOffset);
	}

	/**
	 * Starts a live template inserting the rule object after the artifact pattern
	 * key, with a {@code name} tab stop and, for branch rules, a {@code generation}
	 * tab stop. The template reformats to the file's JSON code style.
	 */
	private record ArtifactRuleInsertHandler(boolean withGeneration) implements InsertHandler<LookupElement> {

		@Override
		public void handleInsert(InsertionContext context, LookupElement item) {

			Project project = context.getProject();
			Editor editor = context.getEditor();

			TemplateManager manager = TemplateManager.getInstance(project);
			Template template = manager.createTemplate("", "");
			prepareKeyPrefix(context, item, template);
			PsiDocumentManager.getInstance(project).commitDocument(editor.getDocument());

			template.addTextSegment(": {\n\"name\": \"");
			template.addVariable("name", new TextExpression("name"), true);
			if (withGeneration) {
				template.addTextSegment("\",\n\"generation\": \"");
				template.addVariable("generation", new TextExpression("generation"), true);
			}
			template.addTextSegment("\"\n}");
			template.setToReformat(true);

			manager.startTemplate(editor, template);
		}

	}

}
