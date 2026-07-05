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

package biz.paluch.dap.maven;

import java.util.LinkedHashMap;
import java.util.Map;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.support.Expression;
import biz.paluch.dap.support.PropertyResolver;
import biz.paluch.dap.util.BetterPsiManager;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;
import com.intellij.psi.xml.XmlFile;
import com.intellij.psi.xml.XmlTag;
import com.intellij.util.concurrency.annotations.RequiresReadLock;
import org.jetbrains.idea.maven.dom.MavenDomUtil;
import org.jetbrains.idea.maven.dom.MavenPropertyResolver;
import org.jetbrains.idea.maven.dom.model.MavenDomProjectModel;
import org.jspecify.annotations.Nullable;

/**
 * Parses a BOM POM's {@code dependencyManagement} section into the managed
 * member map by walking the
 * {@code dependencyManagement/dependencies/dependency} tags.
 *
 * @author Mark Paluch
 */
public class MavenBomParser {

	private final @Nullable XmlFile pomFile;

	private final PropertyResolver propertyResolver;

	@RequiresReadLock
	public MavenBomParser(Project project, VirtualFile file) {

		PsiFile psiFile = BetterPsiManager.getInstance(project).findFile(file);
		if (psiFile instanceof XmlFile xmlFile) {
			this.pomFile = xmlFile;
			MavenDomProjectModel domModel = MavenDomUtil.getMavenDomModel(pomFile, MavenDomProjectModel.class);
			this.propertyResolver = domModel != null ? new DomPropertyResolver(xmlFile, domModel)
					: new MavenProjectMetadataPropertyResolver(xmlFile);
		} else {
			this.pomFile = null;
			propertyResolver = PropertyResolver.empty();
		}
	}

	/**
	 * Parse the managed members of the BOM POM file.
	 *
	 * @return the managed members keyed by artifact coordinates; {@literal null} if
	 * the file is no Maven POM.
	 */
	@RequiresReadLock
	public @Nullable Map<ArtifactId, ArtifactVersion> readMembers() {

		if (!(pomFile instanceof XmlFile xmlFile)) {
			return null;
		}

		XmlTag root = xmlFile.getDocument() != null ? xmlFile.getDocument().getRootTag() : null;
		if (root == null) {
			return null;
		}

		Map<ArtifactId, ArtifactVersion> members = new LinkedHashMap<>();
		for (XmlTag dependencyManagement : root.findSubTags("dependencyManagement")) {
			for (XmlTag dependencies : dependencyManagement.findSubTags("dependencies")) {
				for (XmlTag dependency : dependencies.findSubTags("dependency")) {
					collectMember(dependency, members);
				}
			}
		}
		return members;
	}

	private void collectMember(XmlTag dependency, Map<ArtifactId, ArtifactVersion> members) {

		if ("import".equals(text(dependency, "scope"))) {
			return;
		}

		String groupId = resolve(text(dependency, "groupId"));
		String artifactId = resolve(text(dependency, "artifactId"));
		String version = resolve(text(dependency, "version"));

		if (groupId == null || artifactId == null || version == null) {
			return;
		}

		ArtifactVersion.from(version)
				.ifPresent(memberVersion -> members.putIfAbsent(ArtifactId.of(groupId, artifactId), memberVersion));
	}

	private @Nullable String resolve(String value) {

		if (!StringUtils.hasText(value)) {
			return null;
		}

		return Expression.from(value).resolve(propertyResolver);
	}

	private static String text(XmlTag tag, String subTag) {
		String value = tag.getSubTagText(subTag);
		return value != null ? value.trim() : "";
	}

	/**
	 * {@link PropertyResolver} backed by {@link MavenPropertyResolver} against the
	 * containing POM's DOM model. {@code project.*} and {@code pom.*}
	 * self-references resolve from the POM's own coordinates, falling back to the
	 * parent coordinates.
	 */
	static class DomPropertyResolver implements PropertyResolver {

		private final MavenDomProjectModel model;

		private final MavenProjectMetadataPropertyResolver projectPropertyResolver;

		DomPropertyResolver(XmlFile xmlFile, MavenDomProjectModel model) {
			this.model = model;
			this.projectPropertyResolver = new MavenProjectMetadataPropertyResolver(
					xmlFile);
		}

		@Override
		public @Nullable String getProperty(String key) {

			if (projectPropertyResolver.containsProperty(key)) {
				return projectPropertyResolver.getProperty(key);
			}

			return MavenPropertyResolver.resolve("${" + key + "}", model);
		}

	}

}
