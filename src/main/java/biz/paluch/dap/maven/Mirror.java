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

/**
 * A {@code settings.xml} mirror that redirects repositories matching its
 * {@code mirrorOf} pattern to a single URL.
 *
 * <p>Matching follows Maven's own {@code DefaultMirrorSelector} semantics.
 *
 * @param id the mirror id, used to look up the matching {@code <server>}
 * credentials; never {@literal null} or blank.
 * @param url the mirror URL that replaces the original repository URL; never
 * {@literal null} or blank.
 * @param mirrorOf the {@code mirrorOf} pattern declaring which repositories
 * this mirror replaces; never {@literal null} or blank.
 * @author Mark Paluch
 */
record Mirror(String id, String url, String mirrorOf) {
}
