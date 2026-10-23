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

package biz.paluch.dap.maven;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URI;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

import biz.paluch.dap.artifact.RepositoryCredentials;
import biz.paluch.dap.util.StringUtils;
import com.intellij.ide.trustedProjects.TrustedProjects;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Key;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.server.MavenDistributionsCache;
import org.jetbrains.idea.maven.utils.MavenUtil;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ReflectionUtils;

/**
 * Loads the effective Maven {@code settings.xml} state used for repository
 * access.
 *
 * <p>For trusted projects, the loader uses the active Maven installation to
 * merge global and user settings, decrypt server passwords, and retain mirror
 * declarations. Credentials are origin-bound (scheme, host, port) when matching
 * repository declarations are present in any settings profile, and the
 * {@code central} server is bound to the standard Maven Central origins.
 * <p>Settings consider {@code repositoryOrigins} and {@code aliases} when using
 * Maven 3.10.
 * <p>The result is cached on the project until explicitly invalidated.
 *
 * @author Mark Paluch
 */
class SettingsXmlLoader {

	private static final Logger LOG = Logger.getInstance(SettingsXmlLoader.class);

	/**
	 * Detects passwords that are still in Maven-encrypted {@code {base64}} form
	 * after a decryption attempt.
	 */
	private static final Pattern STILL_ENCRYPTED = Pattern.compile("\\{[^}]+\\}");

	private static final Map<File, URLClassLoader> MAVEN_CLASSLOADERS = new ConcurrentHashMap<>();

	private static final Key<MavenSettings> SETTINGS_KEY = Key.create("dependency-assistant.maven.settings");

	private SettingsXmlLoader() {
	}

	/**
	 * Load settings for repository access.
	 * <p>Untrusted projects and unavailable Maven installations yield empty
	 * settings. Results for trusted projects are cached until
	 * {@link #invalidate(Project)}. Failures that escape loading are not cached.
	 */
	public static MavenSettings load(Project project) {

		if (!TrustedProjects.isProjectTrusted(project)) {
			return MavenSettings.empty();
		}

		MavenSettings cached = project.getUserData(SETTINGS_KEY);
		if (cached != null) {
			return cached;
		}

		cached = project.getUserData(SETTINGS_KEY);
		if (cached == null) {
			cached = loadSettings(project);
			project.putUserData(SETTINGS_KEY, cached);
		}
		return cached;
	}

	static void invalidate(Project project) {
		project.putUserData(SETTINGS_KEY, null);
	}

	private static MavenSettings loadSettings(Project project) {

		MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);
		if (mavenManager == null) {
			return MavenSettings.empty();
		}

		Path mavenHomePath = MavenDistributionsCache.getInstance(project)
				.getSettingsDistribution().getMavenHome();
		File mavenHome = mavenHomePath.toFile();
		if (!mavenHome.isDirectory()) {
			LOG.debug("Maven home not resolved. Skipping settings.xml credential loading");
			return MavenSettings.empty();
		}

		// Global settings (lower priority) <maven_home>/conf/settings.xml.
		Path globalSettings = new File(mavenHome, "conf/settings.xml").toPath();

		// Let IntelliJ resolve overrides and remote EEL paths for user settings.
		String configuredUserSettings = mavenManager.getGeneralSettings().getUserSettingsFile();
		Path userSettings = MavenUtil.resolveUserSettingsPath(configuredUserSettings, project);

		// settings-security.xml for master-password decryption.
		String securityFilePath = System.getProperty("settings.security",
				System.getProperty("user.home") + "/.m2/settings-security.xml");

		URLClassLoader mavenClassLoader = MAVEN_CLASSLOADERS.computeIfAbsent(mavenHome, it -> {

			File libDir = new File(mavenHome, "lib");
			if (!libDir.isDirectory()) {
				LOG.debug("Maven lib dir not found at " + libDir + ", skipping credential loading");
			}

			try {
				return new URLClassLoader(collectJars(libDir), null);
			} catch (IOException e) {
				LOG.debug("Failed to list Maven lib JARs from " + libDir, e);
				throw new RuntimeException(e);
			}
		});

		return loadWithMavenApi(mavenClassLoader, globalSettings, userSettings, securityFilePath);
	}

	private static MavenSettings loadWithMavenApi(URLClassLoader loader, Path globalSettings,
			Path userSettings, String securityFilePath) {

		try {
			Object mergedSettings = mergeSettings(loader, globalSettings, userSettings);
			if (mergedSettings == null) {
				return MavenSettings.empty();
			}
			return extractCredentials(loader, mergedSettings, securityFilePath);
		} catch (Exception e) {
			LOG.warn("Error loading credentials from Maven settings via Maven API", e);
			return MavenSettings.empty();
		}
	}

	private static @Nullable Object mergeSettings(URLClassLoader loader, Path globalSettings, Path userSettings)
			throws Exception {

		Class<?> readerClass = loader.loadClass("org.apache.maven.settings.io.xpp3.SettingsXpp3Reader");
		Object reader = readerClass.getConstructor().newInstance();
		Method readMethod = readerClass.getMethod("read", InputStream.class);

		Object global = readSettingsFile(reader, readMethod, globalSettings);
		Object user = readSettingsFile(reader, readMethod, userSettings);

		if (global == null && user == null) {
			return null;
		}

		if (global == null) {
			return user;
		}
		if (user == null) {
			return global;
		}

		// Merge: user settings are dominant (higher priority).
		Class<?> mergerClass = loader.loadClass("org.apache.maven.settings.merge.MavenSettingsMerger");
		Class<?> settingsClass = loader.loadClass("org.apache.maven.settings.Settings");
		Object merger = mergerClass.getConstructor().newInstance();
		Method merge = mergerClass.getMethod("merge", settingsClass, settingsClass, String.class);
		merge.invoke(merger, user, global,
				"user-level");

		return user;
	}

	private static @Nullable Object readSettingsFile(Object reader, Method readMethod, Path path) {
		if (!Files.isRegularFile(path)) {
			return null;
		}
		try (InputStream in = Files.newInputStream(path)) {
			return readMethod.invoke(reader, in);
		} catch (Exception e) {
			LOG.debug("Could not read/parse Maven settings at " + path, e);
			return null;
		}
	}

	private static MavenSettings extractCredentials(URLClassLoader loader, Object settingsObj,
			String securityFilePath) throws Exception {

		Map<String, List<URI>> repositoryBindings = extractServerRepositoryBindings(loader, settingsObj);

		List<Object> servers = getDecryptedServers(loader, settingsObj, securityFilePath);
		Class<?> serverClass = loader.loadClass("org.apache.maven.settings.Server");
		Method getId = serverClass.getMethod("getId");
		Method getUsername = serverClass.getMethod("getUsername");
		Method getPassword = serverClass.getMethod("getPassword");
		Method getRepositoryOrigins = ReflectionUtils.findMethod(serverClass, "getRepositoryOrigins");
		Method getAliases = ReflectionUtils.findMethod(serverClass, "getAliases");

		Map<String, RepositoryCredentials> credentials = new LinkedHashMap<>();

		for (Object server : servers) {

			String id = (String) getId.invoke(server);
			String username = (String) getUsername.invoke(server);
			String password = (String) getPassword.invoke(server);

			if (StringUtils.isEmpty(id) || StringUtils.isEmpty(username) || StringUtils.isEmpty(password)) {
				continue;
			}

			id = id.trim();

			if (STILL_ENCRYPTED.matcher(password.trim()).matches()) {
				LOG.debug("Skipping server '" + id + "': Password could not be decrypted");
				continue;
			}

			List<String> ids = getIds(id, server, getAliases);
			for (String credentialId : ids) {
				List<URI> bases = getBaseUrls(credentialId, repositoryBindings, server, getRepositoryOrigins);
				credentials.put(credentialId, new RepositoryCredentials(id, username.trim(), password.trim(), bases));
			}
		}

		return new MavenSettings(credentials, extractMirrors(loader, settingsObj));
	}

	@SuppressWarnings("unchecked")
	private static List<Object> getDecryptedServers(URLClassLoader loader, Object settingsObj, String securityFilePath)
			throws ReflectiveOperationException {

		Class<?> cipherClass = loader.loadClass("org.sonatype.plexus.components.cipher.DefaultPlexusCipher");
		Object cipher = cipherClass.getConstructor().newInstance();

		Class<?> plexusCipherIface = loader.loadClass("org.sonatype.plexus.components.cipher.PlexusCipher");
		Class<?> dispatcherClass = loader
				.loadClass("org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher");
		Constructor<?> dispatcherCtor = dispatcherClass.getConstructor(plexusCipherIface, Map.class, String.class);
		Object dispatcher = dispatcherCtor.newInstance(cipher, Collections.emptyMap(), securityFilePath);

		Class<?> secDispatcherIface = loader.loadClass("org.sonatype.plexus.components.sec.dispatcher.SecDispatcher");
		Class<?> decrypterClass = loader.loadClass("org.apache.maven.settings.crypto.DefaultSettingsDecrypter");
		Object decrypter = decrypterClass.getConstructor(secDispatcherIface).newInstance(dispatcher);

		Class<?> settingsClass = loader.loadClass("org.apache.maven.settings.Settings");
		Class<?> requestClass = loader.loadClass("org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest");
		Object request = requestClass.getConstructor(settingsClass).newInstance(settingsObj);

		Class<?> requestIface = loader.loadClass("org.apache.maven.settings.crypto.SettingsDecryptionRequest");
		Method decryptMethod = decrypterClass.getMethod("decrypt", requestIface);
		Object result = decryptMethod.invoke(decrypter, request);

		Class<?> resultIface = loader.loadClass("org.apache.maven.settings.crypto.SettingsDecryptionResult");
		return (List<Object>) resultIface.getMethod("getServers").invoke(result);
	}

	@SuppressWarnings("unchecked")
	private static List<String> getIds(String id, Object server, @Nullable Method getAliases) {

		List<String> ids = new ArrayList<>();
		ids.add(id);

		if (getAliases != null) {
			ReflectionUtils.makeAccessible(getAliases);
			List<String> aliases = (List<String>) ReflectionUtils.invokeMethod(getAliases, server);

			if (aliases != null) {
				ids.addAll(aliases);
			}
		}

		return ids;
	}

	@SuppressWarnings("unchecked")
	private static List<URI> getBaseUrls(String id, Map<String, List<URI>> repositoryBindings, Object server,
			@Nullable Method getRepositoryOrigins) {

		List<URI> boundUris = new ArrayList<>();
		List<URI> bindings = repositoryBindings.get(id);
		if (bindings == null && "central".equalsIgnoreCase(id)) {
			bindings = List.of(URI.create("https://repo1.maven.org/maven2/"),
					URI.create("https://repo.maven.apache.org/maven2/"));
		}
		if (bindings != null) {
			boundUris.addAll(bindings);
		}

		if (getRepositoryOrigins != null) {

			ReflectionUtils.makeAccessible(getRepositoryOrigins);
			List<String> origins = (List<String>) ReflectionUtils.invokeMethod(getRepositoryOrigins, server);
			if (origins != null) {
				for (String origin : origins) {
					if (StringUtils.isEmpty(origin)) {
						continue;
					}
					try {
						boundUris.add(URI.create(origin));
					} catch (IllegalArgumentException e) {
						LOG.debug("Skipping invalid repository origin '%s' in settings for server id '%s'"
								.formatted(origin, id), e);
					}
				}
			}
		}

		return boundUris;
	}

	@SuppressWarnings("unchecked")
	private static List<Mirror> extractMirrors(URLClassLoader loader, Object settingsObj) throws Exception {

		Class<?> settingsClass = loader.loadClass("org.apache.maven.settings.Settings");
		List<Object> mirrors = (List<Object>) settingsClass.getMethod("getMirrors")
				.invoke(settingsObj);
		if (mirrors == null || mirrors.isEmpty()) {
			return List.of();
		}

		Class<?> mirrorClass = loader.loadClass("org.apache.maven.settings.Mirror");
		Method getId = mirrorClass.getMethod("getId");
		Method getUrl = mirrorClass.getMethod("getUrl");
		Method getMirrorOf = mirrorClass.getMethod("getMirrorOf");

		List<Mirror> result = new ArrayList<>();
		for (Object mirror : mirrors) {

			String id = (String) getId.invoke(mirror);
			String url = (String) getUrl.invoke(mirror);
			String mirrorOf = (String) getMirrorOf.invoke(mirror);

			if (StringUtils.isEmpty(id) || StringUtils.isEmpty(url) || StringUtils.isEmpty(mirrorOf)) {
				continue;
			}

			result.add(new Mirror(id.trim(), url.trim(), mirrorOf.trim()));
		}

		return result;
	}

	@SuppressWarnings("unchecked")
	private static Map<String, List<URI>> extractServerRepositoryBindings(URLClassLoader loader, Object settingsObj)
			throws Exception {

		Map<String, List<URI>> byId = new LinkedHashMap<>();

		Class<?> settingsClass = loader.loadClass("org.apache.maven.settings.Settings");
		Method getMirrors = settingsClass.getMethod("getMirrors");
		List<Object> mirrors = (List<Object>) getMirrors.invoke(settingsObj);

		Class<?> mirrorClass = loader.loadClass("org.apache.maven.settings.Mirror");
		if (mirrors != null) {
			Method mirrorGetId = mirrorClass.getMethod("getId");
			Method mirrorGetUrl = mirrorClass.getMethod("getUrl");
			for (Object mirror : mirrors) {
				addRepositoryBinding(byId, (String) mirrorGetId.invoke(mirror), (String) mirrorGetUrl.invoke(mirror));
			}
		}

		Method getProfiles = settingsClass.getMethod("getProfiles");

		List<Object> profiles = (List<Object>) getProfiles.invoke(settingsObj);

		if (profiles != null) {
			Class<?> profileClass = loader.loadClass("org.apache.maven.settings.Profile");
			Method getRepositories = profileClass.getMethod("getRepositories");
			Method getPluginRepositories = profileClass.getMethod("getPluginRepositories");
			Class<?> repoClass = loader.loadClass("org.apache.maven.settings.Repository");
			Method repoGetId = repoClass.getMethod("getId");
			Method repoGetUrl = repoClass.getMethod("getUrl");

			for (Object profile : profiles) {
				collectRepositoryBindings(byId, repoGetId, repoGetUrl, getRepositories.invoke(profile));
				collectRepositoryBindings(byId, repoGetId, repoGetUrl, getPluginRepositories.invoke(profile));
			}
		}

		return byId;
	}

	@SuppressWarnings("unchecked")
	private static void collectRepositoryBindings(Map<String, List<URI>> byId, Method repoGetId, Method repoGetUrl,
			@Nullable Object repositoryList) throws Exception {

		if (repositoryList == null) {
			return;
		}

		List<Object> repositories = (List<Object>) repositoryList;
		for (Object repository : repositories) {
			addRepositoryBinding(byId, (String) repoGetId.invoke(repository), (String) repoGetUrl.invoke(repository));
		}
	}

	private static void addRepositoryBinding(Map<String, List<URI>> byId, @Nullable String id, @Nullable String url) {

		if (StringUtils.isEmpty(id) || StringUtils.isEmpty(url)
				|| (!url.startsWith("http://") && !url.startsWith("https://"))) {
			return;
		}

		try {
			byId.computeIfAbsent(id.trim(), k -> new ArrayList<>()).add(URI.create(url.trim()));
		} catch (IllegalArgumentException e) {
			LOG.debug("Skipping invalid repository URL '%s' in settings for server id '%s'".formatted(url, id), e);
		}
	}

	private static URL[] collectJars(File libDir) throws IOException {

		File[] jars = libDir.listFiles((dir, name) -> name.endsWith(".jar"));
		if (jars == null || jars.length == 0) {
			return new URL[0];
		}
		URL[] urls = new URL[jars.length];
		for (int i = 0; i < jars.length; i++) {
			urls[i] = jars[i].toURI().toURL();
		}
		return urls;
	}

}
