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

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.DependencyCollector;
import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RemoteRepositoryReleaseSource;
import biz.paluch.dap.artifact.RepositoryCredentials;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.util.StringUtils;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.lang.properties.IProperty;
import com.intellij.lang.properties.psi.PropertiesFile;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.TextRange;
import com.intellij.psi.PsiFile;
import com.intellij.psi.util.PsiTreeUtil;
import org.jspecify.annotations.Nullable;

/**
 * Parser for {@code .mvn/wrapper/maven-wrapper.properties}.
 *
 * <p>Supports {@code distributionUrl} and {@code wrapperUrl} properties.
 *
 * @author Mark Paluch
 */
class MavenWrapperParser {

	public static final Pattern MAVEN_ARTIFACT_PATTERN = Pattern.compile(
			"(?<groupId>[\\w/]+)/(?<artifactId>[\\w.-]+)/(?<version1>[\\w.-]+)/" +
					"\\k<artifactId>-(?<version2>\\k<version1>)(?<tail>[^/]*)");

	private static final String REPOSITORY_ID = "maven-wrapper";

	private final DependencyCollector collector;

	public MavenWrapperParser() {
		this(new DependencyCollector());
	}

	public MavenWrapperParser(DependencyCollector collector) {
		this.collector = collector;
	}

	/**
	 * Parse the recognized properties from the given wrapper file.
	 * @param file the {@code maven-wrapper.properties} PSI file.
	 * @return the recognized entries, in declaration order; possibly empty.
	 */
	public List<WrapperEntry> parse(PsiFile file) {
		return file instanceof PropertiesFile propsFile ? getEntries(propsFile) : List.of();
	}

	/**
	 * Parse supported properties from the given wrapper {@link PropertiesFile} and
	 * register it in the {@link DependencyCollector}.
	 */
	public void parse(PropertiesFile propsFile) {

		Set<RemoteRepository> repositories = new HashSet<>();
		for (IProperty property : propsFile.getProperties()) {
			parse(property, it -> {

				ArtifactVersion version = it.version();
				VersionSource versionSource = StringUtils.hasText(it.rawVersion())
						? VersionSource.declared(it.rawVersion())
						: VersionSource.none();
				collector.registerDeclaration(it.property().artifactId(), DeclarationSource.dependency(),
						versionSource);

				if (version != null) {
					collector.registerUsage(it.property().artifactId(), version, DeclarationSource.dependency(),
							versionSource);
				}

				repositories.add(it.repository);
			});
		}

		repositories.forEach(it -> collector.addReleaseSource(new RemoteRepositoryReleaseSource(it)));
	}

	/**
	 * Parse supported properties from the given wrapper {@link PropertiesFile}.
	 * @param propsFile the wrapper properties file.
	 * @return the supported entries, in declaration order; possibly empty.
	 */
	public List<WrapperEntry> getEntries(PropertiesFile propsFile) {

		List<WrapperEntry> result = new ArrayList<>();
		for (IProperty property : propsFile.getProperties()) {
			parse(property, result::add);
		}
		return result;
	}

	/**
	 * Check whether the property key
	 * @param key
	 * @return
	 */
	public static boolean isWrapperProperty(@Nullable String key) {

		for (WrapperProperty value : WrapperProperty.values()) {
			if (value.key.equals(key)) {
				return true;
			}
		}
		return false;
	}

	/**
	 * Attempt to parse a {@link IProperty} into an {@link WrapperEntry}.
	 */
	public static @Nullable WrapperEntry parse(IProperty property) {
		return WrapperProperty.parse(property);
	}

	public static void parse(IProperty property, Consumer<WrapperEntry> entryConsumer) {
		parse((PropertyImpl) property, entryConsumer);
	}

	public static void parse(PropertyImpl property, Consumer<WrapperEntry> entryConsumer) {

		if (!property.getPsiElement().isValid()) {
			return;
		}

		for (WrapperProperty wp : WrapperProperty.values()) {
			WrapperEntry entry = wp.parseProperty(property);
			if (entry != null) {
				entryConsumer.accept(entry);
			}
		}
	}

	/**
	 * Return whether the raw property text contains an unescaped trailing backslash
	 * followed by a CR/LF the Java Properties line-continuation idiom. Such values
	 * are silently rejected by the parser (see spec "Non-Goals").
	 * @param rawText the raw PSI text of a {@code PropertyValueImpl}.
	 */
	static boolean containsLineContinuation(String rawText) {

		int index = 0;
		while (index < rawText.length()) {

			char c = rawText.charAt(index);
			if (c != '\\') {
				index++;
				continue;
			}

			int run = 0;
			int i = index;
			while (i < rawText.length() && rawText.charAt(i) == '\\') {
				run++;
				i++;
			}
			if (run % 2 == 1 && i < rawText.length()) {
				char next = rawText.charAt(i);
				if (next == '\n' || next == '\r') {
					return true;
				}
			}
			index = i;
		}
		return false;
	}

	public enum WrapperProperty {

		DISTRIBUTION("distributionUrl", "distributionSha256Sum", ArtifactId.of("org.apache.maven", "apache-maven")),

		WRAPPER("wrapperUrl", "wrapperSha256Sum", ArtifactId.of("org.apache.maven.wrapper", "maven-wrapper"));

		private final String key;

		private final String shaKey;

		private final ArtifactId artifactId;

		WrapperProperty(String key, String shaKey, ArtifactId artifactId) {
			this.key = key;
			this.shaKey = shaKey;
			this.artifactId = artifactId;
		}

		String key() {
			return key;
		}

		String shaKey() {
			return shaKey;
		}

		ArtifactId artifactId() {
			return artifactId;
		}

		/**
		 * Attempt to parse a {@link IProperty} into an {@link WrapperEntry}.
		 */
		public static @Nullable WrapperEntry parse(IProperty property) {

			for (WrapperProperty wp : WrapperProperty.values()) {
				WrapperEntry entry = wp.parseProperty((PropertyImpl) property);
				if (entry != null) {
					return entry;
				}
			}

			return null;
		}

		/**
		 * Attempt to parse a {@link IProperty} into an {@link WrapperEntry}.
		 */
		public @Nullable WrapperEntry parseProperty(PropertyImpl property) {

			if (!key().equals(property.getUnescapedKey())) {
				return null;
			}

			PropertyValueImpl value = PsiTreeUtil.findChildOfType(property, PropertyValueImpl.class);

			if (value == null) {
				return null;
			}

			if (containsLineContinuation(value.getText())) {
				return null;
			}

			try {
				URI uri = URI.create(property.getUnescapedValue());
				RepositoryCredentials credentials = parseCredentials(property.getProject(), uri);
				RemoteRepository repository = parseRemoteRepository(uri, credentials);
				String path = uri.getPath();
				String groupIdPath = artifactId().groupId().replace('.', '/');
				String artifactPath = "/" + artifactId().artifactId() + "/";
				int artifactIndex = path.lastIndexOf(artifactPath);
				if (artifactIndex < 0) {
					return null;
				}

				String groupPath = path.substring(0, artifactIndex);
				if (!groupPath.endsWith("/" + groupIdPath) && !groupPath.equals(groupIdPath)) {
					return null;
				}

				int versionStart = artifactIndex + artifactPath.length();
				int versionEnd = path.indexOf('/', versionStart);
				if (versionEnd < 0) {
					return null;
				}

				String version1 = path.substring(versionStart, versionEnd);
				String fileName = path.substring(versionEnd + 1);
				String filePrefix = artifactId().artifactId() + "-" + version1;
				if (!fileName.startsWith(filePrefix)) {
					return null;
				}

				return new WrapperEntry(this, value, repository, value.getTextRange(), value.getTextRange(), version1);
			} catch (RuntimeException ignored) {
				return null;
			}
		}

		private RemoteRepository parseRemoteRepository(URI uri, @Nullable RepositoryCredentials credentials) {

			URI defaultMaven = URI.create(RemoteRepository.mavenCentral().url());
			if (credentials == null && RemoteRepositoryReleaseSource.hasSameBaseUri(uri, defaultMaven)) {
				return RemoteRepository.mavenCentral();
			}

			return new RemoteRepository(REPOSITORY_ID, uri.toASCIIString(), credentials);
		}

		private @Nullable RepositoryCredentials parseCredentials(Project project, URI uri) {

			if (!TrustedProjects.isProjectTrusted(project)) {
				return null;
			}

			String userInfo = uri.getUserInfo();
			if (StringUtils.hasText(userInfo)) {
				int colon = userInfo.indexOf(':');
				if (colon >= 0) {
					String user = URLDecoder.decode(userInfo.substring(0, colon), StandardCharsets.UTF_8);
					String pass = URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8);
					return new RepositoryCredentials(REPOSITORY_ID, user, pass, List.of(uri));
				}
				String user = URLDecoder.decode(userInfo, StandardCharsets.UTF_8);
				return new RepositoryCredentials(REPOSITORY_ID, user, "", List.of(uri));
			}
			return null;
		}

	}

	/**
	 * Parsed wrapper declaration.
	 */
	public record WrapperEntry(WrapperProperty property,
			PropertyValueImpl versionLiteral, RemoteRepository repository, TextRange pathVersionRange,
			TextRange fileVersionRange, String rawVersion) {

		public boolean hasArtifactId(ArtifactId coordinate) {
			return property.artifactId().equals(coordinate);
		}

		@Nullable
		public ArtifactVersion version() {
			return ArtifactVersion.from(rawVersion).orElse(null);
		}

		public VersionSource versionSource() {
			return StringUtils.hasText(rawVersion) ? VersionSource.declared(rawVersion) : VersionSource.none();
		}

	}

}
