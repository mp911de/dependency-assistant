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

package biz.paluch.dap.state;

import java.util.ArrayList;
import java.util.List;

import com.intellij.util.xmlb.annotations.Attribute;
import com.intellij.util.xmlb.annotations.Tag;
import com.intellij.util.xmlb.annotations.XCollection;

/**
 * Persistent cache entry for a single repository and its known tags.
 * <p>The entry is keyed by an opaque repository key and stores the browsable
 * repository URL along with the tag names in a serializer-friendly
 * representation.
 *
 * @author Mark Paluch
 */
@Tag("repository")
public class CachedRepository {

	private @Attribute String key;

	private @Attribute String url;

	/**
	 * Epoch-millisecond timestamp of the last write to this entry, or {@code 0} if
	 * the entry pre-dates expiry tracking and should never be expired.
	 */
	@Attribute
	private long lastSeen = 0L;

	/**
	 * Failed-lookup counter or epoch-millisecond timestamp of the last completed
	 * tag scan: {@code 0} means never scanned, values below the failure threshold
	 * count consecutive failed lookups, larger values record when the scan
	 * completed (or gave up) and delay the next attempt by the scan interval.
	 */
	@Attribute
	private volatile long lastUpdateTimestamp = 0L;

	private final @XCollection(propertyElementName = "tags", elementName = "tag", valueAttributeName = "name", style = XCollection.Style.v2) List<String> tags = new ArrayList<>();

	/**
	 * Create an empty cache entry for XML deserialization.
	 */
	public CachedRepository() {
	}

	public CachedRepository(String key, String url) {
		this.key = key;
		this.url = url;
	}

	public String getKey() {
		return key;
	}

	public String getUrl() {
		return url;
	}

	public void setUrl(String url) {
		this.url = url;
	}

	public long getLastSeen() {
		return lastSeen;
	}

	public void setLastSeen(long lastSeen) {
		this.lastSeen = lastSeen;
	}

	public long getLastUpdateTimestamp() {
		return lastUpdateTimestamp;
	}

	public void setLastUpdateTimestamp(long lastUpdateTimestamp) {
		this.lastUpdateTimestamp = lastUpdateTimestamp;
	}

	public List<String> getTags() {
		synchronized (this.tags) {
			return List.copyOf(this.tags);
		}
	}

	public void setTags(List<String> tags) {
		synchronized (this.tags) {
			this.tags.clear();
			this.tags.addAll(tags);
		}
	}

	public CachedRepository snapshot() {

		CachedRepository copy = new CachedRepository(key, url);
		copy.lastSeen = this.lastSeen;
		copy.lastUpdateTimestamp = this.lastUpdateTimestamp;
		copy.setTags(getTags());
		return copy;
	}

	@Override
	public String toString() {
		return key + " (" + url + ")";
	}

}
