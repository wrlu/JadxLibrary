package com.wrlus.jadx.library.universal.utils;

import jadx.api.JavaClass;

public class JavaClassWithSuper {
	public final JavaClass baseClass;
	public final String superClassName;

	public JavaClassWithSuper(JavaClass baseClass, String superClassName) {
		this.baseClass = baseClass;
		this.superClassName = superClassName;
	}
}
