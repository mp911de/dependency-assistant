/*
 * Copyright 2026 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package biz.paluch.dap.assertions;

import java.util.ArrayList;
import java.util.List;

import biz.paluch.dap.support.NewerVersionLineMarkerProvider;
import com.intellij.codeInsight.daemon.GutterMark;
import com.intellij.codeInsight.daemon.LineMarkerInfo;
import com.intellij.openapi.util.Key;
import com.intellij.psi.PsiFile;
import com.intellij.psi.SyntaxTraverser;
import org.assertj.core.api.AssertProvider;

/**
 * Test support for collecting and asserting IntelliJ gutter marks for a
 * {@link PsiFile}.
 * <p>Instances are cached in {@link PsiFile} user data so that repeated
 * assertions can reuse the same collected marker state for a given file
 * fixture.
 *
 * @author Mark Paluch
 */
public class LineMarkers implements AssertProvider<GutterMarksAssert> {

	private static final Key<LineMarkers> LINE_MARKERS = Key.create("line-markers");

	private final List<GutterMark> gutterMarks;

	private LineMarkers(List<GutterMark> gutterMarks) {
		this.gutterMarks = gutterMarks;
	}

	/**
	 * Collect the gutter marks exposed by {@link NewerVersionLineMarkerProvider}
	 * for the given file.
	 * <p>The resulting {@code LineMarkers} instance is cached in the file's user
	 * data and returned from subsequent invocations.
	 *
	 * @param file the PSI file to inspect; must not be {@literal null}.
	 * @return the collected line markers for the given file.
	 */
	public static LineMarkers of(PsiFile file) {

		LineMarkers lineMarkers = file.getUserData(LINE_MARKERS);

		if (lineMarkers != null) {
			return lineMarkers;
		}

		List<GutterMark> gutterMarks = new ArrayList<>();
		NewerVersionLineMarkerProvider provider = new NewerVersionLineMarkerProvider();

		SyntaxTraverser.psiTraverser(file).forEach(element -> {

			LineMarkerInfo<?> lineMarkerInfo = provider.getLineMarkerInfo(element);
			if (lineMarkerInfo == null) {
				return;
			}

			gutterMarks.add(new LineMarkerInfo.LineMarkerGutterIconRenderer<>(lineMarkerInfo));
		});

		lineMarkers = new LineMarkers(gutterMarks);
		file.putUserData(LINE_MARKERS, lineMarkers);

		return lineMarkers;
	}

	/**
	 * Return an AssertJ facade for the collected gutter marks.
	 *
	 * @return an assertion object for the collected gutter marks.
	 */
	@Override
	public GutterMarksAssert assertThat() {
		return new GutterMarksAssert(gutterMarks);
	}

}
