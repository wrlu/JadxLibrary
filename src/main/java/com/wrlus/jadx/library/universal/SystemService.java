package com.wrlus.jadx.library.universal;

import com.wrlus.jadx.library.LibraryEntry;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaMethod;
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

public class SystemService implements LibraryEntry {
	private static final List<String> romPaths = new ArrayList<>();
	private static final String androidFrameworkPath = "/packages/android";
	private static final String serviceListPath = "/service_list.txt";
	private static final String accessibleServicesPath = "/accessible_services.txt";
	private static final String accessibleAidlPath = "/accessible_aidl.txt";
	private static final String binderServiceAidlPath = "/binder_service_aidl.txt";
	private static final String binderAnonymousAidlPath = "/binder_anonymous_aidl.txt";
	private static final String binderServiceAidlImplPath = "/binder_service_aidl_impl.txt";

	private static final String AIDL_DEFAULT = "Default";
	private static final String AIDL_STUB = "Stub";
	private static final String AIDL_STUB_PROXY = "Proxy";
	private static final String BINDER_SERVICE_AIDL = "binder_service_aidl";
	private static final String BINDER_ANONYMOUS_AIDL = "binder_anonymous_aidl";

	private static int binderServiceAidlCount = 0;
	private static int binderAnonymousAidlCount = 0;

	static {
		 romPaths.add("D:/Users/xiaolu/Firmware/Android/Google/shiba_beta-bp22.250221.015");
//		 romPaths.add("D:/Users/xiaolu/Firmware/Android/Huawei/BLK-AL00_104.2.0.191");
//		 romPaths.add("D:/Users/xiaolu/Firmware/Android/Honor/ELI-AN00_9.0.0.165");
//		 romPaths.add("D:/Users/xiaolu/Firmware/Android/OPPO/PJV110_15_SP1A.210812.016_U.1cfadf7_1-c6eb");
//		 romPaths.add("D:/Users/xiaolu/Firmware/Android/Vivo/PD2364_15_AP3A.240905.015.A2_compiler250220193957");
//		 romPaths.add("D:/Users/xiaolu/Firmware/Android/Xiaomi/vermeer_AQ3A.240912.001_OS2.0.102.0.VNKCNXM");
	}

	static class AidlInfo {
		public JavaClass innerClassDefault;
		public JavaClass innerClassStub;
		public JavaClass innerClassStubProxy;

		public boolean valid() {
			return innerClassDefault != null &&
					innerClassStub != null &&
					innerClassStubProxy != null;
		}
	}

	@Override
	public void onLibraryLoaded(String[] args) {
		if (args.length > 1) {
			processRom(args[1]);
			return;
		}
		for (String romPath : romPaths) {
			processRom(romPath);
		}
	}

	public static void processRom(String romPath) {
		binderServiceAidlCount = 0;
		binderAnonymousAidlCount = 0;

		List<String> serviceList = getServiceList(romPath);
		List<String> accessibleServices = getAccessibleServiceList(romPath);

		JadxDecompiler jadxDecompiler = initDecompiler(new File(romPath, androidFrameworkPath));
		if (jadxDecompiler != null) {
			Map<String, List<AidlDefinition>> aidl =
					searchAidlDefinition(jadxDecompiler, serviceList, accessibleServices, romPath);
			searchAidlImpl(jadxDecompiler, aidl.get(BINDER_SERVICE_AIDL), romPath);

			jadxDecompiler.close();
		}

		System.out.println("binderServiceAidlCount = " + binderServiceAidlCount +
				", binderAnonymousAidlCount = " + binderAnonymousAidlCount);
	}

	public static JadxDecompiler initDecompiler(File frameworkDir) {
		File[] frameworkJars = frameworkDir.listFiles();
		if (frameworkJars == null) {
			System.err.println("Permission denied: " + frameworkDir.getAbsolutePath());
			return null;
		}
		JadxArgs jadxArgs = new JadxArgs();
		jadxArgs.setInputFiles(Arrays.asList(frameworkJars));
		JadxDecompiler jadx = new JadxDecompiler(jadxArgs);
		jadx.load();
		return jadx;
	}

	public static List<String> getServiceList(String romPath) {
		File serviceListFile = new File(romPath, serviceListPath);
		if (!serviceListFile.exists()) {
			System.err.println("No such file or directory: " + serviceListFile.getAbsolutePath());
			return new ArrayList<>();
		}
		try {
			BufferedReader br = new BufferedReader(new FileReader(serviceListFile));
			Stream<String> lines = br.lines();
			List<String> result = new ArrayList<>();
			lines.forEach(s -> {
				if (!(s.contains("Found ") && s.contains(" services:"))) {
					result.add(s);
				}
			});
			br.close();
			return result;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new ArrayList<>();
	}

	public static List<String> getAccessibleServiceList(String romPath) {
		File accessibleServicesFile = new File(romPath, accessibleServicesPath);
		if (!accessibleServicesFile.exists()) {
			System.err.println("No such file or directory: " + accessibleServicesFile.getAbsolutePath());
			return new ArrayList<>();
		}
		try {
			BufferedReader br = new BufferedReader(new FileReader(accessibleServicesFile));
			Stream<String> lines = br.lines();
			List<String> result = new ArrayList<>();
			lines.forEach(result::add);
			br.close();
			return result;
		} catch (IOException e) {
			e.printStackTrace();
		}
		return new ArrayList<>();
	}

	public static Map<String, List<AidlDefinition>> searchAidlDefinition(JadxDecompiler jadxDecompiler, List<String> serviceList,
														  List<String> accessibleServices, String romPath) {
		List<AidlDefinition> binderServiceAidl = new ArrayList<>();
		List<AidlDefinition> binderAnonymousAidl = new ArrayList<>();

		for (JavaClass cls : jadxDecompiler.getClassesWithInners()) {
			AidlInfo aidlInfo = extractAidlSubclasses(cls);
			if (aidlInfo.valid()) {
				AidlDefinition aidlDefinition = generateAidlDefinition(cls, aidlInfo);
				String writeableStr = getAidlWriteableString(aidlDefinition);
				String outputPath;

				String service = isServiceManagerAidl(cls, serviceList);
				if (service != null) {
					binderServiceAidl.add(aidlDefinition);
					outputPath = binderServiceAidlPath;
					++binderServiceAidlCount;
					if (isAccessibleService(service, accessibleServices)) {
						writeToFile(writeableStr, new File(romPath, accessibleAidlPath), true);
					}
				} else {
					binderAnonymousAidl.add(aidlDefinition);
					outputPath = binderAnonymousAidlPath;
					++binderAnonymousAidlCount;
				}
				writeToFile(writeableStr, new File(romPath, outputPath), true);
			}
		}
		Map<String, List<AidlDefinition>> result = new HashMap<>();
		result.put(BINDER_SERVICE_AIDL, binderServiceAidl);
		result.put(BINDER_ANONYMOUS_AIDL, binderAnonymousAidl);
		return result;
	}

	public static void searchAidlImpl(JadxDecompiler jadxDecompiler, List<AidlDefinition> aidlDefinitions, String romPath) {
		Map<String, JavaClass> stubSuperClassMap = new HashMap<>();
		for (JavaClass cls : jadxDecompiler.getClassesWithInners()) {
			ClassNode classNode = cls.getClassNode();
			if (classNode.getSuperClass() == null) {
				continue;
			}
			classNode.visitSuperTypes((parent, type) -> {
				String superClassName = type.toString();
				if (superClassName.endsWith("$Stub")) {
					stubSuperClassMap.put(superClassName, cls);
					System.out.println(cls.getFullName() + " extends " + superClassName);
				}
			});
		}
		File output = new File(romPath, binderServiceAidlImplPath);
		for (AidlDefinition aidlDefinition : aidlDefinitions) {
			String stubClass = aidlDefinition.interfaceToken + "$Stub";
			if (stubSuperClassMap.containsKey(stubClass)) {
				JavaClass implClass = stubSuperClassMap.get(stubClass);
				aidlDefinition.implementationClass = implClass.getFullName();
				writeToFile(aidlDefinition.implementationClass + '\n', output, true);
				List<String> methodImplementation = new ArrayList<>();
				for (JavaMethod method : implClass.getMethods()) {
					if (aidlDefinition.methodDefinition.contains(getMethodDefinitionStr(method))) {
						String code = method.getMethodNode().getCodeStr();
						methodImplementation.add(code);
						writeToFile(code + '\n', output, true);
					}
				}
				aidlDefinition.methodImplementation = methodImplementation;
				System.out.println("Found implementation `" + aidlDefinition.implementationClass +
						"` for AIDL `" + aidlDefinition.interfaceToken + "`");
			}
		}

	}

	static AidlInfo extractAidlSubclasses(JavaClass cls) {
		AidlInfo aidlInfo = new AidlInfo();
		List<JavaClass> innerClasses = cls.getInnerClasses();
		for (JavaClass innerClass : innerClasses) {
			if (innerClass.getName().equals(AIDL_DEFAULT)) {
				aidlInfo.innerClassDefault = innerClass;
			} else if (innerClass.getName().equals(AIDL_STUB)) {
				aidlInfo.innerClassStub = innerClass;
			}
			if (aidlInfo.innerClassDefault != null && aidlInfo.innerClassStub != null)
				break;
		}
		if (aidlInfo.innerClassDefault != null && aidlInfo.innerClassStub != null) {
			List<JavaClass> stubInnerClasses = aidlInfo.innerClassStub.getInnerClasses();
			for (JavaClass innerClass : stubInnerClasses) {
				if (innerClass.getName().equals(AIDL_STUB_PROXY)) {
					aidlInfo.innerClassStubProxy = innerClass;
					break;
				}
			}
		}
		return aidlInfo;
	}

	static String isServiceManagerAidl(JavaClass cls, List<String> serviceList) {
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

	static boolean isAccessibleService(String service, List<String> accessibleServices) {
		if (accessibleServices == null) {
			return false;
		}
		for (String serviceName : accessibleServices) {
			if (service.contains(serviceName + ":")) {
				return true;
			}
		}
		return false;
	}

	static String getAidlWriteableString(AidlDefinition aidlDefinition) {
		StringBuilder sb = new StringBuilder();
		sb.append(aidlDefinition.interfaceToken);
		sb.append('\n');
		for (String methodStr: aidlDefinition.methodDefinitionWithParams) {
			sb.append(methodStr);
			sb.append('\n');
		}
		sb.append('\n');
		return sb.toString();
	}

	static void writeToFile(String writeableStr, File outputFile, boolean append) {
		try {
			FileWriter fw = new FileWriter(outputFile, append);
			fw.write(writeableStr);
			fw.flush();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	static String getAidlMethodString(JavaMethod method) {
		return beautyMethodCodeStr(method.getMethodNode().getCodeStr());
	}

	static String beautyMethodCodeStr(String codeStr) {
		String beautyCodeStr = "";
		String[] lines = codeStr.split("\n");
		for (String line : lines) {
			if (line.contains("(") && line.contains(")")) {
				beautyCodeStr = line;
			}
		}
		beautyCodeStr = beautyCodeStr.replace("throws RemoteException", "");
		beautyCodeStr = beautyCodeStr.replace("{", "");
		beautyCodeStr = beautyCodeStr.trim();
		beautyCodeStr = beautyCodeStr + ";";
		return beautyCodeStr;
	}

	static AidlDefinition generateAidlDefinition(JavaClass aidlCls, AidlInfo aidlInfo) {
		AidlDefinition aidlDefinition = new AidlDefinition();
		aidlDefinition.interfaceToken = aidlCls.getFullName();
		aidlDefinition.implementationClass = null;

		aidlDefinition.methodDefinition = new ArrayList<>();
		aidlDefinition.methodDefinitionWithParams = new ArrayList<>();

		for (JavaMethod mth : aidlCls.getMethods()) {
			aidlDefinition.methodDefinition.add(getMethodDefinitionStr(mth));
		}
		for (JavaMethod mth : aidlInfo.innerClassDefault.getMethods()) {
			System.out.println(getMethodDefinitionStr(mth));
			if (aidlDefinition.methodDefinition.contains(getMethodDefinitionStr(mth))) {
				aidlDefinition.methodDefinitionWithParams.add(getAidlMethodString(mth));
			}
		}
		return aidlDefinition;
	}

	static String getMethodDefinitionStr(JavaMethod method) {
		return method.toString().replace(method.getDeclaringClass().getClassNode().toString() + ".", "");
	}
}
