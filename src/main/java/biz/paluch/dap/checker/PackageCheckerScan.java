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

package biz.paluch.dap.checker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Function;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.HasArtifactId;
import biz.paluch.dap.artifact.PackageIdentity;
import biz.paluch.dap.artifact.PackageSystem;
import com.intellij.openapi.project.Project;
import com.intellij.packageChecker.model.Package;
import com.intellij.packageChecker.model.PackageType;
import com.intellij.packageChecker.service.Malicious;
import com.intellij.packageChecker.service.PackageChecker;
import com.intellij.packageChecker.service.PackageStatus;
import com.intellij.packageChecker.service.Unchecked;
import com.intellij.packageChecker.service.Vulnerable;
import org.jspecify.annotations.Nullable;

/**
 * Single-use translation of one {@link CheckRequest} into the Package Checker
 * model and of the resulting statuses back into a {@link CheckResult}.
 *
 * <p>Versions of an unsupported {@link PackageSystem} are dropped on
 * {@link #create(Project, CheckRequest) creation}. A malicious status becomes
 * one synthesized critical advisory.
 *
 * <p>The class must only be loaded when the Package Checker plugin is present.
 *
 * @author Mark Paluch
 * @see PackageCheckerVulnerabilitySource
 */
class PackageCheckerScan implements BiConsumer<Package, PackageStatus> {

	private static final Vulnerability MALICIOUS = new Vulnerability("MALICIOUS", null, null, "Malicious package", 10.0,
			CvssSeverity.CRITICAL, "");

	private final Project project;

	private final List<SupportedVersion> versions;

	private final Map<Package, SupportedVersion> owners = new LinkedHashMap<>();

	private final CheckResult.Builder result = CheckResult.builder();

	private PackageCheckerScan(Project project, List<SupportedVersion> versions) {
		this.project = project;
		this.versions = versions;
	}

	/**
	 * Create a scan for the versions of the given request that Package Checker can
	 * evaluate.
	 *
	 * @param project the project whose context packages resolve against.
	 * @param request the packages and versions to evaluate.
	 * @return the scan, {@link #isEmpty() empty} if no version is supported.
	 */
	static PackageCheckerScan create(Project project, CheckRequest request) {

		List<SupportedVersion> versions = new ArrayList<>(request.size());
		request.forEach((ecosystemPackage, requested) -> {

			PackageType packageType = toPackageType(ecosystemPackage.getPackageSystem());
			if (packageType == null) {
				return;
			}

			for (ArtifactVersion version : requested) {
				versions.add(new SupportedVersion(ecosystemPackage, packageType, version));
			}
		});

		return new PackageCheckerScan(project, versions);
	}

	/**
	 * Return whether packages of the given system can be translated into the
	 * Package Checker model.
	 *
	 * @param packageSystem the package system to check support for.
	 * @return {@literal true} if the package system is supported; {@literal false}
	 * otherwise.
	 */
	static boolean supports(PackageSystem packageSystem) {
		return toPackageType(packageSystem) != null;
	}

	/**
	 * Return whether the request contained no version Package Checker can evaluate.
	 *
	 * @return {@literal true} if no supported version remains; {@literal false}
	 * otherwise.
	 */
	boolean isEmpty() {
		return versions.isEmpty();
	}

	/**
	 * Return the packages that Package Checker has not evaluated yet, recording the
	 * already-known statuses in the result along the way.
	 *
	 * <p>Already-known statuses carry no fresh scan to force, so only the returned
	 * packages need to be sent to
	 * {@link PackageCheckerDelegate#checkPackages(PackageChecker, List)}. Calling
	 * this method more than once repeats the lookup and is not intended.
	 *
	 * @param packageStatus the current status lookup, typically
	 * {@link PackageChecker#packageStatus(Package)}.
	 * @return the packages awaiting a scan, empty if every version is known.
	 */
	List<Package> pending(Function<Package, PackageStatus> packageStatus) {

		List<Package> packages = new ArrayList<>(versions.size());
		for (SupportedVersion version : versions) {

			Package ecosystemPackage = version.toPackage(project);
			PackageStatus status = packageStatus.apply(ecosystemPackage);

			if (status instanceof Unchecked) {
				owners.put(ecosystemPackage, version);
				packages.add(ecosystemPackage);
			} else {
				result.add(version.pkg(), version.version(), toVulnerabilities(status));
			}
		}

		return packages;
	}

	/**
	 * Record the scanned status of a package previously reported as
	 * {@code pending}.
	 *
	 * <p>A package this scan did not request is ignored.
	 *
	 * @param ecosystemPackage the scanned package.
	 * @param status the status Package Checker returned.
	 */
	@Override
	public void accept(Package ecosystemPackage, PackageStatus status) {

		SupportedVersion owner = owners.get(ecosystemPackage);
		if (owner == null) {
			return;
		}

		result.add(owner.pkg(), owner.version(), toVulnerabilities(status));
	}

	/**
	 * Return the result collected so far, covering the already-known and the
	 * scanned versions.
	 *
	 * @return the check result.
	 */
	public CheckResult toCheckResult() {
		return result.build();
	}

	/**
	 * Return the vulnerabilities for a specific version.
	 */
	Vulnerabilities getVulnerabilities(PackageIdentity pkg, ArtifactVersion version) {
		return toCheckResult().getVulnerabilities(pkg, version);
	}

	private static Vulnerabilities toVulnerabilities(PackageStatus status) {

		if (status instanceof Vulnerable vulnerable) {

			List<com.intellij.packageChecker.model.Vulnerability> found = vulnerable.getVulnerablePackage()
					.getVulnerabilities();
			List<Vulnerability> vulnerabilities = new ArrayList<>(found.size());
			for (com.intellij.packageChecker.model.Vulnerability vulnerability : found) {
				vulnerabilities.add(toVulnerability(vulnerability));
			}
			return Vulnerabilities.of(vulnerabilities);
		}

		if (status instanceof Malicious) {
			return Vulnerabilities.of(MALICIOUS);
		}

		return Vulnerabilities.clean();
	}

	private static Vulnerability toVulnerability(com.intellij.packageChecker.model.Vulnerability vulnerability) {

		double cvssScore = vulnerability.getCvssScore();
		return new Vulnerability(vulnerability.getId(), vulnerability.getCve(), null, vulnerability.getTitle(),
				cvssScore, CvssSeverity.fromScore(cvssScore), vulnerability.getReference());
	}

	private static @Nullable PackageType toPackageType(PackageSystem packageSystem) {
		return switch (packageSystem) {
		case MAVEN -> PackageType.maven;
		case NPM -> PackageType.npm;
		default -> null;
		};
	}

	record SupportedVersion(PackageIdentity pkg, PackageType packageType,
			ArtifactVersion version) implements HasArtifactId {

		@Override
		public ArtifactId getArtifactId() {
			return pkg.getArtifactId();
		}

		Package toPackage(Project project) {

			return Package.Companion.create(project, packageType(), getNamespace(),
					pkg().getArtifactId().artifactId(), version().toString());
		}

		private String getNamespace() {

			ArtifactId artifactId = pkg.getArtifactId();
			if (pkg.getPackageSystem() == PackageSystem.NPM && artifactId.groupId().equals(artifactId.artifactId())) {
				return "";
			}
			return artifactId.groupId();
		}

	}

}
