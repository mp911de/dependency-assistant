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

package biz.paluch.dap.assistant;

import biz.paluch.dap.artifact.ArtifactRelease;
import com.intellij.openapi.util.Key;
import com.intellij.openapi.util.UserDataHolder;
import com.intellij.psi.PsiElement;
import org.jspecify.annotations.Nullable;

/**
 * @author Mark Paluch
 */
public class ArtifactReleaseAccessor {

	private static final Key<ArtifactRelease> KEY = new Key<>("ArtifactRelease");

	public static @Nullable ArtifactRelease getRelease(@Nullable UserDataHolder psiElement) {
		return psiElement != null ? psiElement.getUserData(KEY) : null;
	}

	public static void putRelease(ArtifactRelease release, UserDataHolder psiElement) {
		psiElement.putUserData(KEY, release);
	}

	public static @Nullable ArtifactRelease getRelease(@Nullable PsiElement psiElement) {
		return psiElement != null ? psiElement.getCopyableUserData(KEY) : null;
	}

	public static void putRelease(ArtifactRelease release, PsiElement psiElement) {
		psiElement.putCopyableUserData(KEY, release);
	}

}
