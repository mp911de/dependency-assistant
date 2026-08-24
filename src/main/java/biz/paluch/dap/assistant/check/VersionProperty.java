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

package biz.paluch.dap.assistant.check;

/**
 * Identity of a version property shared by dependencies handled through one
 * dependency assistant.
 *
 * <p>Profile and module scope are deliberately absent: coupling uses the stable
 * assistant id and bare property name only.
 *
 * @author Mark Paluch
 * @param assistantId the stable dependency-assistant id.
 * @param property the bare version-property name.
 */
public record VersionProperty(String assistantId, String property) {

}
