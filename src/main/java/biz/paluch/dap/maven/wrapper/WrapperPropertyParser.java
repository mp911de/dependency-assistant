package biz.paluch.dap.maven.wrapper;

import java.net.URI;
import java.net.URISyntaxException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.regex.Matcher;

import biz.paluch.dap.artifact.RemoteRepository;
import biz.paluch.dap.artifact.RepositoryCredentials;
import biz.paluch.dap.util.PropertyUtils;
import biz.paluch.dap.util.StringUtils;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.lang.properties.psi.impl.PropertyImpl;
import com.intellij.lang.properties.psi.impl.PropertyValueImpl;
import org.jspecify.annotations.Nullable;

/**
 * Parser for {@link WrapperProperty}s.
 *
 * @author Mark Paluch
 */
class WrapperPropertyParser {

	/**
	 * Parse the property as this wrapper property.
	 *
	 * @param property the property to parse.
	 * @return the parsed wrapper entry, or {@literal null} if the property does not
	 * match this wrapper property.
	 */
	public static @Nullable WrapperEntry parseProperty(WrapperProperty wrapperProperty, PropertyImpl property) {

		PropertyValueImpl value = PropertyUtils.findPropertyValue(property);
		if (!wrapperProperty.key().equals(property.getUnescapedKey()) || value == null
		    || PropertyUtils.containsLineContinuation(value.getText())) {
			return null;
		}

		String decoded = property.getUnescapedValue();
		if (StringUtils.isEmpty(decoded)) {
			return null;
		}

		URI uri;
		try {
			uri = URI.create(decoded);
		}
		catch (IllegalArgumentException malformed) {
			return null;
		}

		RemoteRepository repository;
		if (TrustedProjects.isProjectTrusted(property.getProject())) {
			RepositoryCredentials credentials = parseCredentials(uri);
			repository = parseRemoteRepository(wrapperProperty, uri, credentials);
		}
		else {
			repository = RemoteRepository.mavenCentral();
		}

		Matcher matcher = MavenWrapperUtils.MAVEN_ARTIFACT_PATTERN.matcher(decoded);
		if (!matcher.find()) {
			return null;
		}

		String pathVersion = matcher.group("version1");
		String fileVersion = matcher.group("version2");
		return new WrapperEntry(wrapperProperty, property, value, repository, pathVersion, fileVersion);
	}

	static RemoteRepository parseRemoteRepository(WrapperProperty wrapperProperty, URI uri, @Nullable RepositoryCredentials credentials) {
		URI repositoryBase = repositoryBaseUri(wrapperProperty, uri);
		if (repositoryBase == null) {
			return RemoteRepository.mavenCentral();
		}
		URI defaultMaven = URI.create(RemoteRepository.mavenCentral().url());
		if (credentials == null && repositoryBase.normalize()
				.equals(defaultMaven.normalize())) {
			return RemoteRepository.mavenCentral();
		}
		return new RemoteRepository(MavenWrapperUtils.REPOSITORY_ID, repositoryBase.toASCIIString(), credentials);
	}

	private static @Nullable URI repositoryBaseUri(WrapperProperty wrapperProperty, URI uri) {

		String rawPath = uri.getRawPath();
		if (StringUtils.isEmpty(rawPath) || uri.getScheme() == null || uri.getRawAuthority() == null) {
			return null;
		}

		String groupPath = "/" + wrapperProperty.canonicalGroupPath();
		int from = 0;
		while (from < rawPath.length()) {
			int start = rawPath.indexOf(groupPath, from);
			if (start < 0) {
				return null;
			}

			int end = start + groupPath.length();
			if (end == rawPath.length() || rawPath.charAt(end) == '/') {
				try {
					return new URI(uri.getScheme(), uri.getRawAuthority(), rawPath.substring(0, start + 1), null,
							null);
				}
				catch (URISyntaxException ex) {
					return null;
				}
			}
			from = end;
		}

		return null;
	}

	private static @Nullable RepositoryCredentials parseCredentials(URI uri) {

		String userInfo = uri.getUserInfo();
		if (StringUtils.hasText(userInfo)) {
			int colon = userInfo.indexOf(':');
			if (colon >= 0) {
				String user = URLDecoder.decode(userInfo.substring(0, colon), StandardCharsets.UTF_8);
				String pass = URLDecoder.decode(userInfo.substring(colon + 1), StandardCharsets.UTF_8);
				return new RepositoryCredentials(MavenWrapperUtils.REPOSITORY_ID, user, pass, List.of(uri));
			}
			String user = URLDecoder.decode(userInfo, StandardCharsets.UTF_8);
			return new RepositoryCredentials(MavenWrapperUtils.REPOSITORY_ID, user, "", List.of(uri));
		}
		return null;
	}
}
