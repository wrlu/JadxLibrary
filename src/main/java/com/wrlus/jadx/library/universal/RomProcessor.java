package com.wrlus.jadx.library.universal;

import com.google.gson.Gson;
import com.google.gson.stream.JsonWriter;
import com.wrlus.jadx.library.universal.utils.AidlClass;
import com.wrlus.jadx.library.universal.utils.AidlMethod;
import com.wrlus.jadx.library.universal.utils.JavaClassWithSuper;
import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.core.dex.nodes.ClassNode;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

public class RomProcessor {
	private final String romPath;
	private final Decompiler decompiler;

	private final List<String> serviceList;
	private final List<String> accessibleServiceList;
	private final List<AidlClass> aidlClassList;

	// Package names
	private static final String PKG_ANDROID = "android";

	// Input file paths
	private static final String PACKAGES_PATH = "/packages/";
	private static final String SERVICE_LIST_PATH = "/service_list.txt";
	private static final String ACCESSIBLE_SERVICES_PATH = "/accessible_services.txt";

	// Output file paths
	private static final String BINDER_SERVICE_AIDL_PATH = "/binder_service_aidl.json";
	private static final String BINDER_ANONYMOUS_AIDL_PATH = "/binder_anonymous_aidl.json";
	private static final String AIDL_CODE_MD_PATH = "/aidl_code.md";
	private static final String AIDL_CODE_PATH = "/aidl_code.json";

	public RomProcessor(String romPath) {
		this.romPath = romPath;
		decompiler = new Decompiler();
		serviceList = new ArrayList<>();
		accessibleServiceList = new ArrayList<>();
		aidlClassList = new ArrayList<>();
	}

	public class Decompiler {
		private JadxDecompiler androidFramework;
		private final Map<String, JadxDecompiler> packages;

		public Decompiler() {
			androidFramework = null;
			packages = new HashMap<>();
		}

		public void initFramework() {
			initPackage(PKG_ANDROID);
		}

		public JadxDecompiler getFramework() {
			if (androidFramework != null) {
				return androidFramework;
			} else {
				System.err.println("JadxDecompiler not initialize, please call initFramework first");
				return null;
			}
		}

		public void initPackage(String packageName) {
			JadxDecompiler decompiler = createJadxDecompiler(packageName);
			if (decompiler != null) {
				decompiler.load();
				if (!packageName.equals(PKG_ANDROID)) {
					packages.put(packageName, decompiler);
				} else {
					androidFramework = decompiler;
				}
			}
		}

		public JadxDecompiler getPackage(String packageName) {
			if (packages.containsKey(packageName)) {
				return packages.get(packageName);
			} else {
				System.err.println("JadxDecompiler not initialize, please call initPackage first");
				return null;
			}
		}

		private JadxDecompiler createJadxDecompiler(String packageName) {
			File packageDir = new File(new File(romPath, PACKAGES_PATH), packageName);
			File[] dexFiles = packageDir.listFiles();
			if (dexFiles == null) {
				System.err.println("Permission denied: " + packageDir.getAbsolutePath());
				return null;
			}
			JadxArgs jadxArgs = new JadxArgs();
			jadxArgs.setInputFiles(Arrays.asList(dexFiles));
			return new JadxDecompiler(jadxArgs);
		}
	}

	public void initServiceList() {
		File serviceListFile = new File(romPath, SERVICE_LIST_PATH);
		if (!serviceListFile.exists()) {
			System.err.println("No such file or directory: " + serviceListFile.getAbsolutePath());
			return;
		}
		serviceList.clear();
		try {
			BufferedReader br = new BufferedReader(new FileReader(serviceListFile));
			Stream<String> lines = br.lines();
			lines.forEach(s -> {
				if (!(s.contains("Found ") && s.contains(" services:"))) {
					serviceList.add(s);
				}
			});
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void initAccessibleServiceList() {
		File accessibleServicesFile = new File(romPath, ACCESSIBLE_SERVICES_PATH);
		if (!accessibleServicesFile.exists()) {
			System.err.println("No such file or directory: " + accessibleServicesFile.getAbsolutePath());
			return;
		}
		accessibleServiceList.clear();
		try {
			BufferedReader br = new BufferedReader(new FileReader(accessibleServicesFile));
			Stream<String> lines = br.lines();
			lines.forEach(accessibleServiceList::add);
			br.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void searchAidlDefinition() {
		aidlClassList.clear();
		for (JavaClass cls : decompiler.androidFramework.getClassesWithInners()) {
			AidlClass aidlClass = new AidlClass(cls);
			aidlClass.extractInner();

			if (aidlClass.isAidl(true)) {
				String service = matchServiceManagerAidl(cls);
				aidlClass.type = service != null ? AidlClass.Type.BINDER : AidlClass.Type.ANONYMOUS;
				aidlClass.accessible = isAccessibleService(service);
				aidlClass.initAidlMethodDefinition();
				aidlClassList.add(aidlClass);
			}
		}
	}

	public void searchAidlImpl() {
		List<JavaClassWithSuper> extendsStubClasses = getExtendsStubClasses();
		for (AidlClass aidlClass : aidlClassList) {
			aidlClass.initAidlMethodImpl(extendsStubClasses);
		}
	}

	// Get all classes which extends class end with `$Stub`.
	private List<JavaClassWithSuper> getExtendsStubClasses() {
		final List<JavaClassWithSuper> extendsStubClasses = new ArrayList<>();
		for (JavaClass cls : decompiler.androidFramework.getClassesWithInners()) {
			ClassNode classNode = cls.getClassNode();
			if (classNode.getSuperClass() == null) {
				continue;
			}
			classNode.visitSuperTypes((parent, type) -> {
				String superClassName = type.toString();
				if (superClassName.endsWith("$Stub")) {
					extendsStubClasses.add(new JavaClassWithSuper(cls, superClassName));
				}
			});
		}
		return extendsStubClasses;
	}

	private String matchServiceManagerAidl(JavaClass cls) {
		if (serviceList == null) {
			return null;
		}
		String fullName = cls.getFullName();
		for (String service : serviceList) {
			if (service.contains(fullName)) {
				return service;
			}
		}
		return null;
	}

	private boolean isAccessibleService(String service) {
		if (service == null || accessibleServiceList == null) {
			return false;
		}
		for (String serviceName : accessibleServiceList) {
			if (service.contains(serviceName + ":")) {
				return true;
			}
		}
		return false;
	}

	public void dumpAidlToFile() {
		try {
			Gson gson = new Gson();
			JsonWriter binderServiceAidlWriter = new JsonWriter(
					new FileWriter(new File(romPath, BINDER_SERVICE_AIDL_PATH)));
			JsonWriter binderAnonymousAidlWriter = new JsonWriter(
					new FileWriter(new File(romPath, BINDER_ANONYMOUS_AIDL_PATH)));

			binderServiceAidlWriter.beginArray();
			binderAnonymousAidlWriter.beginArray();
			for (AidlClass aidlClass : aidlClassList) {
				if (aidlClass.type == AidlClass.Type.BINDER) {
					binderServiceAidlWriter.jsonValue(gson.toJson(aidlClass));
					binderServiceAidlWriter.flush();
				} else if (aidlClass.type == AidlClass.Type.ANONYMOUS) {
					binderAnonymousAidlWriter.jsonValue(gson.toJson(aidlClass));
					binderAnonymousAidlWriter.flush();
				}
			}
			binderServiceAidlWriter.endArray();
			binderAnonymousAidlWriter.endArray();
			binderServiceAidlWriter.close();
			binderAnonymousAidlWriter.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void dumpAidlCodeToMarkdown() {
		try {
			FileWriter writer = new FileWriter(new File(romPath, AIDL_CODE_MD_PATH));
			writer.write("# AIDL Code");
			writer.write('\n');
			writer.write("* ROM Path: " + romPath);
			writer.write('\n');
			for (AidlClass aidlClass : aidlClassList) {
				if (aidlClass.type == AidlClass.Type.BINDER) {
					writer.write("## " + aidlClass.interfaceClassName);
					writer.write('\n');
					writer.write("* Implementation: " + aidlClass.implClassName);
					writer.write('\n');
					writer.write("* Accessible: " + aidlClass.accessible);
					writer.write('\n');
					for (AidlMethod aidlMethod : aidlClass.methods) {
						writer.write("### " + aidlMethod.definition);
						writer.write('\n');
						writer.write("```java");
						writer.write('\n');
						if (aidlMethod.code != null) {
							writer.write(aidlMethod.code);
						}
						writer.write('\n');
						writer.write("```");
						writer.write('\n');
					}
				}
			}
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	public void dumpAidlCodeToFile() {
		try {
			JsonWriter writer = new JsonWriter(
					new FileWriter(new File(romPath, AIDL_CODE_PATH)));
			writer.beginArray();
			for (AidlClass aidlClass : aidlClassList) {
				if (aidlClass.type == AidlClass.Type.BINDER && aidlClass.accessible) {
					writer.beginObject();
					writer.name("interfaceClass").value(aidlClass.interfaceClassName);
					writer.name("implClass").value(aidlClass.implClassName);
					writer.name("type").value(aidlClass.type.name());
					writer.name("accessible").value(aidlClass.accessible);
					writer.name("methods").beginArray();
					for (AidlMethod aidlMethod : aidlClass.methods) {
						if (aidlMethod.code != null && !aidlMethod.code.contains
								("throw new UnsupportedOperationException(\"Method not decompiled:")) {
							writer.beginObject();
							writer.name("fullDefinition").value(aidlMethod.fullDefinition);
							writer.name("code").value(aidlMethod.code);
							writer.endObject();
						}
					}
					writer.endArray();
					writer.endObject();
				}
			}
			writer.endArray();
			writer.close();
		} catch (IOException e) {
			e.printStackTrace();
		}

	}

	public String getRomPath() {
		return romPath;
	}

	public Decompiler getDecompiler() {
		return decompiler;
	}

	public List<String> getServiceList() {
		return serviceList;
	}

	public List<String> getAccessibleServiceList() {
		return accessibleServiceList;
	}

	public List<AidlClass> getAidlClassList() {
		return aidlClassList;
	}
}
