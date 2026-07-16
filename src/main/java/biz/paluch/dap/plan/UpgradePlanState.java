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

package biz.paluch.dap.plan;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.BiFunction;

import biz.paluch.dap.artifact.ArtifactId;
import biz.paluch.dap.artifact.ArtifactVersion;
import biz.paluch.dap.artifact.DeclarationSource;
import biz.paluch.dap.artifact.Dependency;
import biz.paluch.dap.artifact.VersionSource;
import biz.paluch.dap.checker.CvssSeverity;
import biz.paluch.dap.checker.Vulnerabilities;
import biz.paluch.dap.ticket.Label;
import biz.paluch.dap.ticket.Milestone;
import biz.paluch.dap.ticket.TicketKey;
import biz.paluch.dap.ticket.TicketSystem;
import biz.paluch.dap.upgrade.UpgradeDecision;
import biz.paluch.dap.util.StringUtils;
import com.intellij.openapi.components.PersistentStateComponent;
import com.intellij.openapi.components.Service;
import com.intellij.openapi.components.State;
import com.intellij.openapi.components.Storage;
import com.intellij.openapi.project.Project;
import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.Transient;
import com.intellij.util.xmlb.annotations.XCollection;
import org.jspecify.annotations.Nullable;

import org.springframework.util.ObjectUtils;

/**
 * Project-level service persisting the Upgrade Plan across IDE restarts.
 *
 * <p>The plan is stored in {@code .idea/dependency-assistant-plan.xml}. That
 * location is committed to version control by default, so a plan can be shared
 * and reviewed like any other project artifact; a team that prefers a
 * local-only plan adds the file to {@code .gitignore}.
 *
 * @author Mark Paluch
 */
@Service(Service.Level.PROJECT)
@State(name = "DependencyAssistantUpgradePlan", storages = @Storage(value = "dependency-assistant-plan.xml"))
final class UpgradePlanState implements PersistentStateComponent<UpgradePlanState.Plan> {

	private final UpgradePlanListener events;

	private final PlanGenerationTracker tracker = new PlanGenerationTracker();

	private Plan state = new Plan();

	public UpgradePlanState(Project project) {
		this.events = project.getMessageBus().syncPublisher(UpgradePlanListener.TOPIC);
	}

	static class PlanGenerationTracker {

		private final AtomicReference<PlanGeneration> tracker = new AtomicReference<>(new PlanGeneration(0, 0));

		PlanGeneration next() {

			while (true) {
				PlanGeneration current = tracker.get();
				long revision = current.revision + 1;
				PlanGeneration next = new PlanGeneration(revision, revision);
				if (tracker.compareAndSet(current, next)) {
					return next;
				}
			}
		}

		PlanGeneration current() {
			return tracker.get();
		}

		boolean isCurrent(PlanGeneration generation) {
			return generation.equals(tracker.get());
		}

		@Nullable
		PlanGeneration tryAdvance(PlanGeneration generation, Runnable r) {
			long revision = generation.revision + 1;
			PlanGeneration next = new PlanGeneration(revision, revision);
			if (tracker.compareAndSet(generation, next)) {
				r.run();
				return next;
			}

			return null;
		}

		@Nullable
		PlanGeneration tryAdvance(PlanGeneration expected, PlanGeneration target, Runnable r) {

			PlanGeneration current = tracker.get();
			if (!current.hasState(expected)) {
				return null;
			}

			long revision = current.revision + 1;
			PlanGeneration next = new PlanGeneration(revision, target.state);
			if (tracker.compareAndSet(current, next)) {
				r.run();
				return next;
			}

			return null;
		}

	}

	/**
	 * Return the project-scoped service instance.
	 *
	 * @param project the IntelliJ project.
	 * @return the corresponding service instance.
	 */
	public static UpgradePlanState getInstance(Project project) {
		return project.getService(UpgradePlanState.class);
	}

	@Override
	public synchronized Plan getState() {
		return state.snapshot();
	}

	public Plan getPlan() {
		return state;
	}

	@Override
	public synchronized void loadState(Plan state) {
		this.tracker.next();
		this.state = state;
		this.events.planReplaced();
	}

	synchronized <T extends @Nullable Object> T doWithContent(BiFunction<PlanGeneration, Content, T> callback) {
		return callback.apply(tracker.current(), state.getContent());
	}

	synchronized boolean isCurrent(PlanGeneration generation) {
		return tracker.isCurrent(generation);
	}

	synchronized @Nullable PlanGeneration advance(PlanGeneration current, Runnable callback) {
		return tracker.tryAdvance(current, callback);
	}

	synchronized @Nullable PlanGeneration advance(PlanGeneration expected, PlanGeneration target, Runnable callback) {
		return tracker.tryAdvance(expected, target, callback);
	}

	/**
	 * Persisted upgrade plan.
	 */
	@Tag("plan")
	static class Plan implements Cloneable {

		@Tag
		public Content content = new Content();

		@Attribute
		private @Nullable String milestone;

		@Transient
		private @Nullable Milestone selectedMilestone;

		@Attribute
		private @Nullable String label;

		@Transient
		private @Nullable Label selectedLabel;

		public Content getContent() {
			return content;
		}

		public void setContent(Content content) {
			this.content = content;
		}

		public @Nullable String getMilestone() {
			return milestone;
		}

		public void setMilestone(@Nullable String milestone) {
			this.milestone = milestone;
		}

		@Transient
		public @Nullable Milestone getSelectedMilestone() {
			return selectedMilestone;
		}

		@Transient
		public void setSelectedMilestone(@Nullable Milestone milestone) {
			this.selectedMilestone = milestone;
			setMilestone(milestone != null ? milestone.getTitle() : null);
		}

		public @Nullable String getLabel() {
			return label;
		}

		public void setLabel(@Nullable String label) {
			this.label = label;
		}

		@Transient
		public @Nullable Label getSelectedLabel() {
			return selectedLabel;
		}

		@Transient
		public void setSelectedLabel(@Nullable Label label) {
			this.selectedLabel = label;
			setLabel(label != null ? label.getName() : null);
		}

		void clearSelectedTicketValues() {
			this.selectedMilestone = null;
			this.selectedLabel = null;
		}

		@Override
		protected Plan clone() {
			try {
				return (Plan) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new UnsupportedOperationException(e);
			}
		}

		public Plan snapshot() {
			Plan snapshot = clone();
			snapshot.setContent(content.snapshot());
			return snapshot;
		}

	}

	/**
	 * The persisted and optionally materialized content of an Upgrade Plan.
	 */
	@Tag("content")
	static class Content implements Iterable<Item> {

		@XCollection(propertyElementName = "items", elementName = "item", style = XCollection.Style.v2)
		private List<Item> items = new ArrayList<>();

		@XCollection(propertyElementName = "affectedFiles", elementName = "file", style = XCollection.Style.v2)
		private List<String> affectedFiles = new ArrayList<>();

		public static Content from(UpgradePlan plan) {
			Content content = new Content();
			content.setItems(plan.stream().map(Item::from).toList());
			content.setAffectedFiles(plan.getScope().getPaths());
			return content;
		}

		public List<Item> getItems() {
			return items;
		}

		public void setItems(List<Item> items) {
			this.items = items;
		}

		public List<String> getAffectedFiles() {
			return affectedFiles;
		}

		public void setAffectedFiles(List<String> affectedFiles) {
			this.affectedFiles = affectedFiles;
		}

		boolean isEmpty() {
			return items.isEmpty();
		}

		@Override
		public Iterator<Item> iterator() {
			return items.iterator();
		}

		public synchronized Content snapshot() {
			Content snapshot = new Content();
			for (Item item : items) {
				snapshot.items.add(item.snapshot());
			}
			snapshot.affectedFiles = List.copyOf(affectedFiles);
			return snapshot;
		}

	}

	/**
	 * XML-serialized state of one planned upgrade unit.
	 *
	 * @author Mark Paluch
	 */
	@Tag("item")
	static class Item implements Cloneable {

		@Transient
		private @Nullable ItemId id;

		@Attribute
		private @Nullable String displayName;

		@Attribute
		private String toVersion = "";

		@Attribute
		private boolean vulnerabilityFix;

		@Attribute
		private int vulnerabilityCount;

		@Attribute
		private CvssSeverity highestVulnerabilitySeverity = CvssSeverity.NONE;

		@Tag
		private @Nullable Ticket ticket;

		@XCollection(propertyElementName = "members", elementName = "member", style = XCollection.Style.v2)
		private List<Member> members = new ArrayList<>();

		@Transient
		private @Nullable UpgradePlanItem materialized;

		static Item from(UpgradePlanCapture capture, ArtifactVersion targetVersion) {

			Item item = new Item();
			item.setToVersion(targetVersion.toString());
			item.displayName = capture.getPlanName();

			Vulnerabilities currentVulnerabilities = Vulnerabilities.clean();
			Vulnerabilities targetVulnerabilities = Vulnerabilities.clean();
			for (UpgradeDecision decision : capture.getUpgradeDecisions()) {
				item.members.add(new Member(decision.getDependency(), capture.getAssistantClassName()));
				currentVulnerabilities = currentVulnerabilities
						.addAll(decision.getVulnerabilities(decision.getCurrentVersion()));
				targetVulnerabilities = targetVulnerabilities.addAll(decision.getVulnerabilities(targetVersion));
			}
			item.vulnerabilityFix = currentVulnerabilities.isVulnerable()
					&& !targetVulnerabilities.isVulnerable();
			item.vulnerabilityCount = currentVulnerabilities.isVulnerable() ? currentVulnerabilities.size() : 0;
			item.highestVulnerabilitySeverity = currentVulnerabilities.isVulnerable()
					? currentVulnerabilities.getHighestSeverity()
					: CvssSeverity.NONE;

			item.getId();
			return item;
		}

		public static Item from(UpgradePlanItem planItem) {

			Item item = new Item();
			item.setDisplayName(planItem.getDisplayName());
			item.setToVersion(planItem.getToVersion().toString());
			item.setVulnerabilityFix(planItem.isVulnerabilityFix());
			item.setVulnerabilityCount(planItem.getVulnerabilityCount());
			item.setHighestVulnerabilitySeverity(planItem.getHighestVulnerabilitySeverity());
			List<Member> members = new ArrayList<>();
			for (int i = 0; i < planItem.getStoredMembers().size(); i++) {
				members.add(new Member(planItem.getStoredMembers().get(i), planItem.getMemberAssistantClassName(i)));
			}
			item.setMembers(members);
			item.setTicket(Ticket.from(planItem.getTicket()));
			item.getId();
			return item;
		}

		public ItemId getId() {
			if (id == null) {
				this.id = createItemId(members);
			}
			return id;
		}

		public @Nullable String getDisplayName() {
			return displayName;
		}

		public void setDisplayName(@Nullable String displayName) {
			this.displayName = displayName;
		}

		public String getToVersion() {
			return toVersion;
		}

		public void setToVersion(String toVersion) {
			this.toVersion = toVersion;
		}

		public boolean isVulnerabilityFix() {
			return vulnerabilityFix;
		}

		public void setVulnerabilityFix(boolean vulnerabilityFix) {
			this.vulnerabilityFix = vulnerabilityFix;
		}

		public int getVulnerabilityCount() {
			return vulnerabilityCount;
		}

		public void setVulnerabilityCount(int vulnerabilityCount) {
			this.vulnerabilityCount = vulnerabilityCount;
		}

		public CvssSeverity getHighestVulnerabilitySeverity() {
			return highestVulnerabilitySeverity;
		}

		public void setHighestVulnerabilitySeverity(CvssSeverity highestVulnerabilitySeverity) {
			this.highestVulnerabilitySeverity = highestVulnerabilitySeverity;
		}

		public @Nullable Ticket getTicket() {
			return ticket;
		}

		public void setTicket(@Nullable Ticket ticket) {
			this.ticket = ticket;
		}

		public List<Member> getMembers() {
			return members;
		}

		public void setMembers(List<Member> members) {
			this.members = members;
		}

		@Transient
		public @Nullable UpgradePlanItem getMaterialized() {
			return materialized;
		}

		@Transient
		public void setMaterialized(@Nullable UpgradePlanItem materialized) {
			this.materialized = materialized;
		}

		public static ItemId createItemId(List<UpgradePlanState.Member> members) {

			Set<ItemId.MemberKey> memberKeys = new HashSet<>();

			for (UpgradePlanState.Member member : members) {
				memberKeys.add(
						new ItemId.MemberKey(member.groupId, member.artifactId, member.fromVersion, member.assistant));
			}

			return new ItemId(memberKeys);
		}

		@Override
		protected Item clone() {
			try {
				return (Item) super.clone();
			} catch (CloneNotSupportedException e) {
				throw new UnsupportedOperationException(e);
			}
		}

		public Item snapshot() {
			Item snapshot = clone();
			snapshot.members = members.stream().map(Member::new).toList();
			UpgradePlanItem materialized = snapshot.getMaterialized();

			if (materialized != null) {
				snapshot.ticket = Ticket.from(materialized.getTicket());
			}

			return snapshot;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Item item)) {
				return false;
			}
			return getId().equals(item.getId());
		}

		@Override
		public int hashCode() {
			return getId().hashCode();
		}

		@Override
		public String toString() {
			return displayName + " -> " + toVersion + " (" + id + ")";
		}

	}

	@Tag("ticket")
	static class Ticket {

		@Attribute
		private @Nullable String key;

		@Attribute
		private @Nullable String url;

		@Attribute
		private @Nullable String repository;

		public Ticket() {
		}

		public Ticket(@Nullable String key, @Nullable String url, @Nullable String repository) {
			this.key = key;
			this.url = url;
			this.repository = repository;
		}

		public static @Nullable Ticket from(@Nullable UpgradeTicket ticket) {
			if (ticket == null) {
				return null;
			}
			return new Ticket(ticket.getKey(), ticket.getUrl(), ticket.getRepository());
		}

		public @Nullable String getKey() {
			return key;
		}

		public void setKey(@Nullable String key) {
			this.key = key;
		}

		public @Nullable String getUrl() {
			return url;
		}

		public void setUrl(@Nullable String url) {
			this.url = url;
		}

		public @Nullable String getRepository() {
			return repository;
		}

		public void setRepository(@Nullable String repository) {
			this.repository = repository;
		}

		public UpgradeTicket toUpgradeTicket(@Nullable TicketSystem ticketSystem) {
			return new UpgradeTicket(key,
					ticketSystem != null ? ticketSystem.getDisplayReference(TicketKey.of(key)) : key, url, repository);
		}

		@Override
		public String toString() {
			return "UpgradeTicket{" +
					"key='" + key + '\'' +
					", url='" + url + '\'' +
					", repository='" + repository + '\'' +
					'}';
		}

	}

	/**
	 * One persisted version source of a plan member.
	 */
	@Tag("versionSource")
	public static class VersionSourceState {

		@Attribute
		public VersionSourceKind kind = VersionSourceKind.DECLARED;

		@Attribute
		public @Nullable String value;

		@Attribute
		public @Nullable String profile;

		@Attribute
		public @Nullable DeclarationKind declaration;

		public VersionSourceState() {
		}

		public VersionSourceState(VersionSourceKind kind, @Nullable String value, @Nullable String profile,
				@Nullable DeclarationKind declaration) {
			this.kind = kind;
			this.value = value;
			this.profile = profile;
			this.declaration = declaration;
		}

		static @Nullable VersionSourceState from(VersionSource source) {

			if (source instanceof VersionSource.VersionDeclarationSource declared) {
				DeclarationSourceState declaration = new DeclarationSourceState(declared.getDeclarationSource());
				return new VersionSourceState(VersionSourceKind.DECLARATION, null, declaration.profileId,
						declaration.kind);
			}
			if (source instanceof VersionSource.VersionCatalogProperty catalogProperty) {
				return new VersionSourceState(VersionSourceKind.CATALOG_PROPERTY, catalogProperty.getProperty(),
						null,
						null);
			}
			if (source instanceof VersionSource.VersionCatalogVersion catalogVersion) {
				return new VersionSourceState(VersionSourceKind.CATALOG_VERSION, catalogVersion.getVersion(), null,
						null);
			}
			if (source instanceof VersionSource.VersionCatalog) {
				return new VersionSourceState(VersionSourceKind.CATALOG, null, null, null);
			}
			if (source instanceof VersionSource.VersionProperty property) {
				String profile = source instanceof VersionSource.Profile profileSource
						? profileSource.getProfileId()
						: null;
				return new VersionSourceState(
						profile != null ? VersionSourceKind.PROFILE_PROPERTY : VersionSourceKind.PROPERTY,
						property.getProperty(), profile, null);
			}
			if (source instanceof VersionSource.VersionPrefix prefix) {
				return new VersionSourceState(VersionSourceKind.PREFIX, prefix.getVersion(), null, null);
			}
			if (source instanceof VersionSource.DeclaredVersion declaredVersion) {
				return new VersionSourceState(VersionSourceKind.DECLARED, declaredVersion.getVersion(), null, null);
			}

			return null;
		}

		@Transient
		VersionSource toVersionSource() {

			return switch (kind) {
			case DECLARED -> StringUtils.isEmpty(value) ? VersionSource.none() : VersionSource.declared(value);
			case PROPERTY -> StringUtils.isEmpty(value) ? VersionSource.none() : VersionSource.property(value);
			case PROFILE_PROPERTY -> StringUtils.isEmpty(value) || StringUtils.isEmpty(profile)
					? VersionSource.none()
					: VersionSource.profileProperty(profile, value);
			case PREFIX -> StringUtils.isEmpty(value) ? VersionSource.none() : VersionSource.prefix(value);
			case CATALOG -> VersionSource.versionCatalog();
			case CATALOG_VERSION -> StringUtils.isEmpty(value) ? VersionSource.versionCatalog()
					: VersionSource.versionCatalog(value);
			case CATALOG_PROPERTY -> StringUtils.isEmpty(value) ? VersionSource.none()
					: VersionSource.versionCatalogProperty(value);
			case DECLARATION -> VersionSource.declared(new DeclarationSourceState(
					declaration != null ? declaration : DeclarationKind.DEPENDENCY, profile).toDeclarationSource());
			};
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof VersionSourceState that)) {
				return false;
			}
			return kind == that.kind && ObjectUtils.nullSafeEquals(value, that.value)
					&& ObjectUtils.nullSafeEquals(profile, that.profile) && declaration == that.declaration;
		}

		@Override
		public int hashCode() {
			return ObjectUtils.nullSafeHash(kind, value, profile, declaration);
		}

		@Override
		public String toString() {
			return "VersionSourceState{" + "kind=" + kind + ", value='" + value + '\'' + ", profile='" + profile
					+ '\'' + ", declaration=" + declaration + '}';
		}

	}

	public enum VersionSourceKind {

		DECLARED,
		PROPERTY,
		PROFILE_PROPERTY,
		PREFIX,
		CATALOG,
		CATALOG_VERSION,
		CATALOG_PROPERTY,
		DECLARATION
	}

	/**
	 * One persisted structural declaration location of a plan member.
	 */
	@Tag("declarationSource")
	public static class DeclarationSourceState {

		@Attribute("id")
		public DeclarationKind kind = DeclarationKind.DEPENDENCY;

		@Attribute("profile")
		public @Nullable String profileId;

		public DeclarationSourceState() {
		}

		public DeclarationSourceState(DeclarationKind kind, @Nullable String profileId) {
			this.kind = kind;
			this.profileId = profileId;
		}

		DeclarationSourceState(DeclarationSource source) {
			this(getKind(source),
					source instanceof DeclarationSource.Profile profile ? profile.getProfileId() : null);
		}

		@Transient
		DeclarationSource toDeclarationSource() {

			boolean root = StringUtils.isEmpty(profileId);
			return switch (kind) {
			case DEPENDENCY -> root ? DeclarationSource.dependency()
					: DeclarationSource.profileDependency(profileId);
			case DEPENDENCY_MANAGEMENT -> root ? DeclarationSource.managed()
					: DeclarationSource.profileManaged(profileId);
			case BOM -> root ? DeclarationSource.bom() : DeclarationSource.profileBom(profileId);
			case PLUGIN -> root ? DeclarationSource.plugin() : DeclarationSource.profilePlugin(profileId);
			case PLUGIN_MANAGEMENT -> root ? DeclarationSource.pluginManagement()
					: DeclarationSource.profilePluginManagement(profileId);
			};
		}

		private static DeclarationKind getKind(DeclarationSource source) {

			if (source instanceof DeclarationSource.Bom) {
				return DeclarationKind.BOM;
			}

			boolean managed = source instanceof DeclarationSource.Managed;
			if (source.isPlugin()) {
				return managed ? DeclarationKind.PLUGIN_MANAGEMENT : DeclarationKind.PLUGIN;
			}
			return managed ? DeclarationKind.DEPENDENCY_MANAGEMENT : DeclarationKind.DEPENDENCY;
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof DeclarationSourceState that)) {
				return false;
			}
			return kind == that.kind && ObjectUtils.nullSafeEquals(profileId, that.profileId);
		}

		@Override
		public int hashCode() {
			return ObjectUtils.nullSafeHash(kind, profileId);
		}

		@Override
		public String toString() {
			return "DeclarationSourceState{" + "kind=" + kind + ", profileId='" + profileId + '\'' + '}';
		}

	}

	public enum DeclarationKind {

		DEPENDENCY,
		DEPENDENCY_MANAGEMENT,
		BOM,
		PLUGIN,
		PLUGIN_MANAGEMENT
	}

	/**
	 * Member artifact of a plan item, carrying its coordinate, current version, the
	 * integration that owns it, and its declaration and version-source structure.
	 */
	@Tag("member")
	public static class Member {

		@Attribute
		public @Nullable String groupId;

		@Attribute
		public @Nullable String artifactId;

		@Attribute
		public @Nullable String fromVersion;

		@Attribute
		public @Nullable String assistant;

		@XCollection(propertyElementName = "declarationSources", elementName = "declarationSource", style = XCollection.Style.v2)
		public List<DeclarationSourceState> declarationSources = new ArrayList<>();

		@XCollection(propertyElementName = "versionSources", elementName = "versionSource", style = XCollection.Style.v2)
		public List<VersionSourceState> versionSources = new ArrayList<>();

		public Member() {
		}

		Member(Member member) {
			this.groupId = member.groupId;
			this.artifactId = member.artifactId;
			this.fromVersion = member.fromVersion;
			this.assistant = member.assistant;
			for (DeclarationSourceState source : member.declarationSources) {
				this.declarationSources.add(new DeclarationSourceState(source.kind, source.profileId));
			}
			for (VersionSourceState source : member.versionSources) {
				this.versionSources.add(new VersionSourceState(source.kind, source.value, source.profile,
						source.declaration));
			}
		}

		Member(Dependency dependency, String assistant) {

			ArtifactId artifactId = dependency.getArtifactId();
			this.groupId = artifactId.groupId();
			this.artifactId = artifactId.artifactId();
			this.fromVersion = dependency.getCurrentVersion().toString();
			this.assistant = assistant;

			for (VersionSource source : dependency.getVersionSources()) {
				VersionSourceState versionSource = VersionSourceState.from(source);
				if (versionSource != null) {
					this.versionSources.add(versionSource);
				}
			}
			for (DeclarationSource source : dependency.getDeclarationSources()) {
				this.declarationSources.add(new DeclarationSourceState(source));
			}
		}

		@Override
		public boolean equals(Object o) {
			if (!(o instanceof Member that)) {
				return false;
			}
			if (!ObjectUtils.nullSafeEquals(groupId, that.groupId)) {
				return false;
			}
			if (!ObjectUtils.nullSafeEquals(artifactId, that.artifactId)) {
				return false;
			}
			if (!ObjectUtils.nullSafeEquals(fromVersion, that.fromVersion)) {
				return false;
			}
			if (!ObjectUtils.nullSafeEquals(assistant, that.assistant)) {
				return false;
			}
			if (!ObjectUtils.nullSafeEquals(declarationSources, that.declarationSources)) {
				return false;
			}
			return ObjectUtils.nullSafeEquals(versionSources, that.versionSources);
		}

		@Override
		public int hashCode() {
			return ObjectUtils.nullSafeHash(groupId, artifactId, fromVersion, assistant, declarationSources,
					versionSources);
		}

		@Override
		public String toString() {
			return groupId + ":" + artifactId + "@" + fromVersion;
		}

	}

}
