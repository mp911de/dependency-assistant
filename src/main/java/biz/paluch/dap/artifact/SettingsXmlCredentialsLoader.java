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
package biz.paluch.dap.artifact;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import org.jetbrains.idea.maven.project.MavenHomeKt;
import org.jetbrains.idea.maven.project.MavenProjectsManager;
import org.jetbrains.idea.maven.project.StaticResolvedMavenHomeType;
import org.jetbrains.idea.maven.utils.MavenUtil;

import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.project.Project;

/**
 * Reads Maven {@code settings.xml} for the active project and returns a map of server-id to
 * {@link RepositoryCredentials}.
 * <p>
 * Uses Maven's own bundled JARs (loaded via {@link URLClassLoader} over {@code <maven_home>/lib}) to parse and decrypt
 * settings, so all password-encryption schemes supported by the active Maven installation are handled correctly.
 * <p>
 * Settings files are resolved and merged in priority order (lowest first):
 * <ol>
 * <li>Global settings: {@code <maven_home>/conf/settings.xml}</li>
 * <li>User settings: path from {@link MavenUtil#resolveUserSettingsPath}, which honours IntelliJ's configured path and
 * falls back to {@code ~/.m2/settings.xml}</li>
 * </ol>
 * <p>
 * Maven-encrypted passwords (those enclosed in {@code {...}}) are decrypted via Maven's
 * {@code DefaultSettingsDecrypter} chain. The master password is read from the file given by the
 * {@code settings.security} system property, defaulting to {@code ~/.m2/settings-security.xml}.
 *
 * @author Mark Paluch
 */
public class SettingsXmlCredentialsLoader {

	private static final Logger LOG = Logger.getInstance(SettingsXmlCredentialsLoader.class);

	/** Detects passwords that are still in Maven-encrypted {@code {base64}} form after a decryption attempt. */
	private static final Pattern STILL_ENCRYPTED = Pattern.compile("\\{[^}]+\\}");

	private final static Map<File, URLClassLoader> MAVEN_CLASSLOADERS = new LinkedHashMap<>();

	private SettingsXmlCredentialsLoader() {}

	/**
	 * Loads credentials from the Maven settings files applicable to the given project.
	 *
	 * @param project the IntelliJ project
	 * @return map from server {@code <id>} to credentials; never {@code null}, may be empty
	 */
	public static Map<String, RepositoryCredentials> load(Project project) {

		MavenProjectsManager mavenManager = MavenProjectsManager.getInstance(project);
		if (mavenManager == null) {
			return Collections.emptyMap();
		}

		// Resolve Maven home — required to locate Maven's own lib JARs.
		// staticOrBundled() maps MavenWrapper and other non-static types to the bundled distribution.
		StaticResolvedMavenHomeType homeType = MavenHomeKt
				.staticOrBundled(mavenManager.getGeneralSettings().getMavenHomeType());
		Path mavenHomePath = MavenUtil.getMavenHomePath(homeType);
		File mavenHome = (mavenHomePath != null) ? mavenHomePath.toFile() : null;
		if (mavenHome == null || !mavenHome.isDirectory()) {
			LOG.debug("Maven home not resolved; skipping settings.xml credential loading");
			return Collections.emptyMap();
		}

		// Global settings (lower priority) — <maven_home>/conf/settings.xml.
		Path globalSettings = new File(mavenHome, "conf/settings.xml").toPath();

		// User settings (higher priority) — resolved via IntelliJ Maven API, which handles
		// the configured override path, default ~/.m2/settings.xml, and remote EEL targets.
		String configuredUserSettings = mavenManager.getGeneralSettings().getUserSettingsFile();
		Path userSettings = MavenUtil.resolveUserSettingsPath(configuredUserSettings, project);

		// settings-security.xml for master-password decryption.
		String securityFilePath = System.getProperty("settings.security",
				System.getProperty("user.home") + "/.m2/settings-security.xml");


		URLClassLoader mavenClassLoader = MAVEN_CLASSLOADERS.computeIfAbsent(mavenHome, it -> {

			File libDir = new File(mavenHome, "lib");
			if (!libDir.isDirectory()) {
				LOG.debug("Maven lib dir not found at " + libDir + "; skipping credential loading");
			}

			try {
				return new URLClassLoader(collectJars(libDir), null);
			}
			catch (IOException e) {
				LOG.debug("Failed to list Maven lib JARs from " + libDir, e);
				throw new RuntimeException(e);
			}
		});

		return loadWithMavenApi(mavenClassLoader, globalSettings, userSettings, securityFilePath);
	}

	// -------------------------------------------------------------------------
	// Settings loading via Maven API
	// -------------------------------------------------------------------------

	private static Map<String, RepositoryCredentials> loadWithMavenApi(URLClassLoader loader, Path globalSettings,
			Path userSettings, String securityFilePath) {

		try {
			Object mergedSettings = mergeSettings(loader, globalSettings, userSettings);
			if (mergedSettings == null) {
				return Collections.emptyMap();
			}
			return extractCredentials(loader, mergedSettings, securityFilePath);
		} catch (Exception e) {
			LOG.debug("Error loading credentials from Maven settings via Maven API", e);
			return Collections.emptyMap();
		}
	}

	/**
	 * Parses global and user {@code settings.xml} files using {@code SettingsXpp3Reader} and merges them (user settings
	 * taking precedence) using {@code MavenSettingsMerger}.
	 */
	private static Object mergeSettings(URLClassLoader loader, Path globalSettings, Path userSettings) throws Exception {

		Class<?> readerClass = loader.loadClass("org.apache.maven.settings.io.xpp3.SettingsXpp3Reader");
		Object reader = readerClass.getConstructor().newInstance();
		Method readMethod = readerClass.getMethod("read", InputStream.class);

		// Read global settings (may not exist — treat as empty).
		Object global = readSettingsFile(reader, readMethod, globalSettings);

		// Read user settings (may not exist — treat as empty).
		Object user = readSettingsFile(reader, readMethod, userSettings);

		if (global == null && user == null) {
			return null;
		}

		// If only one side exists, return it directly without merging.
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
		mergerClass.getMethod("merge", settingsClass, settingsClass, String.class).invoke(merger, user, global,
				"user-level");

		return user; // user now contains the merged result
	}

	private static Object readSettingsFile(Object reader, Method readMethod, Path path) {
		if (path == null || !Files.isRegularFile(path)) {
			return null;
		}
		try (InputStream in = Files.newInputStream(path)) {
			return readMethod.invoke(reader, in);
		} catch (Exception e) {
			LOG.debug("Could not read/parse Maven settings at " + path, e);
			return null;
		}
	}

	/**
	 * Decrypts the server passwords in {@code settingsObj} using Maven's {@code DefaultSettingsDecrypter} chain and
	 * returns a map of server-id to {@link RepositoryCredentials}. Servers whose passwords remain encrypted after the
	 * attempt (decryption failed) are silently omitted.
	 */
	private static Map<String, RepositoryCredentials> extractCredentials(URLClassLoader loader, Object settingsObj,
			String securityFilePath) throws Exception {

		Class<?> cipherClass = loader.loadClass("org.sonatype.plexus.components.cipher.DefaultPlexusCipher");
		Object cipher = cipherClass.getConstructor().newInstance();

		Class<?> plexusCipherIface = loader.loadClass("org.sonatype.plexus.components.cipher.PlexusCipher");
		Class<?> dispatcherClass = loader.loadClass("org.sonatype.plexus.components.sec.dispatcher.DefaultSecDispatcher");
		Constructor<?> dispatcherCtor = dispatcherClass.getConstructor(plexusCipherIface, Map.class, String.class);
		Object dispatcher = dispatcherCtor.newInstance(cipher, Collections.emptyMap(), securityFilePath);

		Class<?> secDispatcherIface = loader.loadClass("org.sonatype.plexus.components.sec.dispatcher.SecDispatcher");
		Class<?> decrypterClass = loader.loadClass("org.apache.maven.settings.crypto.DefaultSettingsDecrypter");
		Object decrypter = decrypterClass.getConstructor(secDispatcherIface).newInstance(dispatcher);

		// Build decryption request from the merged Settings object.
		Class<?> settingsClass = loader.loadClass("org.apache.maven.settings.Settings");
		Class<?> requestClass = loader.loadClass("org.apache.maven.settings.crypto.DefaultSettingsDecryptionRequest");
		Object request = requestClass.getConstructor(settingsClass).newInstance(settingsObj);

		// Decrypt.
		Class<?> requestIface = loader.loadClass("org.apache.maven.settings.crypto.SettingsDecryptionRequest");
		Method decryptMethod = decrypterClass.getMethod("decrypt", requestIface);
		Object result = decryptMethod.invoke(decrypter, request);

		// Extract server credentials from the result.
		Class<?> resultIface = loader.loadClass("org.apache.maven.settings.crypto.SettingsDecryptionResult");
		@SuppressWarnings("unchecked")
		List<Object> servers = (List<Object>) resultIface.getMethod("getServers").invoke(result);

		Class<?> serverClass = loader.loadClass("org.apache.maven.settings.Server");
		Method getId = serverClass.getMethod("getId");
		Method getUsername = serverClass.getMethod("getUsername");
		Method getPassword = serverClass.getMethod("getPassword");

		Map<String, RepositoryCredentials> credentials = new LinkedHashMap<>();

		for (Object server : servers) {
			String id = (String) getId.invoke(server);
			String username = (String) getUsername.invoke(server);
			String password = (String) getPassword.invoke(server);

			if (id == null || id.isBlank() || username == null || username.isBlank() || password == null
					|| password.isBlank()) {
				continue;
			}

			if (STILL_ENCRYPTED.matcher(password.trim()).matches()) {
				LOG.debug("Skipping server '" + id + "' — password could not be decrypted");
				continue;
			}

			credentials.put(id.trim(), new RepositoryCredentials(id, username.trim(), password.trim()));
		}

		return credentials;
	}

	// -------------------------------------------------------------------------
	// Utilities
	// -------------------------------------------------------------------------

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
