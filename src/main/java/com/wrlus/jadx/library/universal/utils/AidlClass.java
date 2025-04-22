package com.wrlus.jadx.library.universal.utils;

import com.google.gson.annotations.SerializedName;
import jadx.api.JavaClass;
import jadx.api.JavaMethod;

import java.util.ArrayList;
import java.util.List;

public class AidlClass {
	@SerializedName("interfaceClass")
	public String interfaceClassName;
	@SerializedName("implClass")
	public String implClassName;
	@SerializedName("type")
	public Type type;
	@SerializedName("accessible")
	public boolean accessible;
	@SerializedName("methods")
	public final List<AidlMethod> methods;

	private transient final JavaClass interfaceClass;
	private transient JavaClass innerClassDefault;
	private transient JavaClass innerClassStub;
	private transient JavaClass innerClassStubProxy;
	private transient JavaClass implClass;

	public static final String AIDL_DEFAULT = "Default";
	public static final String AIDL_STUB = "Stub";
	public static final String AIDL_STUB_PROXY = "Proxy";

	public AidlClass(JavaClass interfaceClass) {
		this.interfaceClass = interfaceClass;
		interfaceClassName = interfaceClass.getFullName();
		implClass = null;
		implClassName = null;
		type = Type.UNKNOWN;
		accessible = false;
		methods = new ArrayList<>();
	}

	public enum Type {
		UNKNOWN, BINDER, ANONYMOUS, OEM
	}

	public void extractInner() {
		List<JavaClass> innerClasses = interfaceClass.getInnerClasses();
		for (JavaClass innerClass : innerClasses) {
			if (innerClass.getName().equals(AIDL_DEFAULT)) {
				innerClassDefault = innerClass;
			} else if (innerClass.getName().equals(AIDL_STUB)) {
				innerClassStub = innerClass;
			}
			if (innerClassDefault != null && innerClassStub != null)
				break;
		}
		if (innerClassDefault != null && innerClassStub != null) {
			List<JavaClass> stubInnerClasses = innerClassStub.getInnerClasses();
			for (JavaClass innerClass : stubInnerClasses) {
				if (innerClass.getName().equals(AIDL_STUB_PROXY)) {
					innerClassStubProxy = innerClass;
					break;
				}
			}
		}
	}

	public boolean isAidl(boolean strict) {
		return strict ? innerClassDefault != null &&
				innerClassStub != null &&
				innerClassStubProxy != null : innerClassDefault != null;
	}

	public void initAidlMethodDefinition() {
		for (JavaMethod interfaceJavaMethod : interfaceClass.getMethods()) {
			AidlMethod aidlMethod = new AidlMethod();
			aidlMethod.definition = AidlMethod.getMethodDefinitionStr(interfaceJavaMethod);

			for (JavaMethod defaultJavaMethod : innerClassDefault.getMethods()) {
				if (aidlMethod.definition.equals(AidlMethod.getMethodDefinitionStr(defaultJavaMethod))) {
					aidlMethod.fullDefinition = AidlMethod.getMethodFullDefinitionStr(defaultJavaMethod);
					methods.add(aidlMethod);
					break;
				}
			}
		}
	}

	public void initAidlMethodImpl(List<JavaClassWithSuper> extendsStubClasses) {
		String stubClassName = interfaceClassName + "$Stub";
		for (JavaClassWithSuper extendsStub : extendsStubClasses) {
			// Match aidl stub
			if (extendsStub.superClassName.equals(stubClassName)) {
				implClass = extendsStub.baseClass;
				implClassName = extendsStub.baseClass.getFullName();
				for (AidlMethod aidlMethod : methods) {
					for (JavaMethod javaMethod : implClass.getMethods()) {
						if (aidlMethod.definition.equals(AidlMethod.getMethodDefinitionStr(javaMethod))) {
							aidlMethod.code = AidlMethod.getMethodCodeStr(javaMethod);
							break;
						}
					}
				}
				break;
			}
		}
	}
}
