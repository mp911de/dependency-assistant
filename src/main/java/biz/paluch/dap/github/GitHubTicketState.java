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

package biz.paluch.dap.github;

import biz.paluch.dap.ticket.TicketState;

/**
 * The two static GitHub issue states.
 *
 * <p>Constant names match the REST API state values case-insensitively, so the
 * enum deserializes directly from issue payloads.
 *
 * @author Mark Paluch
 */
enum GitHubTicketState implements TicketState {

	OPEN(true),

	CLOSED(false);

	private final boolean open;

	GitHubTicketState(boolean open) {
		this.open = open;
	}

	@Override
	public boolean isOpen() {
		return open;
	}

}
