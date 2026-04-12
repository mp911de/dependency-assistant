package biz.paluch.dap.gradle;

import com.intellij.psi.PsiElement;

/**
 * Location of a version string inside a {@code gradle.properties} entry or a
 * property declaration in a build script (e.g. {@code extra["…"]} in
 * Groovy/Kotlin build scripts).
 * 
 * @author Mark Paluch
 */
record PsiPropertyValueElement(PsiElement element, String propertyKey, String propertyValue) {

}
