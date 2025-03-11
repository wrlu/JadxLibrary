package com.wrlus.jadx.library.universal;

import com.wrlus.jadx.library.LibraryEntry;

import jadx.api.JadxArgs;
import jadx.api.JadxDecompiler;
import jadx.api.JavaClass;
import jadx.api.JavaMethod;
import jadx.core.dex.nodes.MethodNode;

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
	private static final String AIDL_DEFAULT = "aidl_default";
	private static final String AIDL_STUB = "aidl_stub";
	private static final String AIDL_STUB_PROXY = "aidl_stub_proxy";

	private static int binderServiceAidlCount = 0;
	private static int binderAnonymousAidlCount = 0;

	static {
		romPaths.add("D:/Users/xiaolu/Firmware/Android/Google/shiba_beta_BP22.250103.008");
		// romPaths.add("D:/Users/xiaolu/Firmware/Android/Huawei/BLK-AL00_104.2.0.182");
		// romPaths.add("D:/Users/xiaolu/Firmware/Android/Honor/ELI-AN00_9.0.0.137");
		// romPaths.add("D:/Users/xiaolu/Firmware/Android/OPPO/PJV110_15_SP1A.210812.016_U.1c05d7b-4e6a-12c6d");
		// romPaths.add("D:/Users/xiaolu/Firmware/Android/Vivo/PD2364_15_AP3A.240905.015.A2_compiler250220193957");
		// romPaths.add("D:/Users/xiaolu/Firmware/Android/Xiaomi/vermeer_AQ3A.240912.001_OS2.0.5.0.VNKCNXM");
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

	private void processRom(String romPath) {
		binderServiceAidlCount = 0;
		binderAnonymousAidlCount = 0;

		File fwkFile = new File(romPath, androidFrameworkPath);
		File serviceListFile = new File(romPath, serviceListPath);
		File accessibleServicesFile = new File(romPath, accessibleServicesPath);

		if (!fwkFile.exists()) {
			System.err.println("No such file or directory: " + fwkFile.getAbsolutePath());
			return;
		}
		if (!serviceListFile.exists()) {
			System.err.println("No such file or directory: " + serviceListFile.getAbsolutePath());
			return;
		}
		File[] frameworkJars = fwkFile.listFiles();
		if (frameworkJars == null) {
			System.err.println("Permission denied: " + fwkFile.getAbsolutePath());
			return;
		}
		List<String> serviceList = getServiceList(serviceListFile);
		List<String> accessibleServices;
		if (accessibleServicesFile.exists()) {
			accessibleServices = getAccessibleServiceList(accessibleServicesFile);
		} else {
			accessibleServices = null;
		}
		for (File frameworkJar : frameworkJars) {
			try {
				processFrameworkJar(frameworkJar, serviceList, accessibleServices, romPath);
			} catch (Exception e) {
				e.printStackTrace();
			}
		}

		System.out.println("binderServiceAidlCount = " + binderServiceAidlCount +
				", binderAnonymousAidlCount = " + binderAnonymousAidlCount);
	}

	private List<String> getServiceList(File serviceListFile) {
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

	private List<String> getAccessibleServiceList(File accessibleServiceListFile) {
		try {
			BufferedReader br = new BufferedReader(new FileReader(accessibleServiceListFile));
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

	private void processFrameworkJar(File frameworkJarFile, List<String> serviceList, List<String> accessibleServices, String romPath) {
		JadxArgs jadxArgs = new JadxArgs();
		jadxArgs.setInputFile(frameworkJarFile);
		JadxDecompiler jadx = new JadxDecompiler(jadxArgs);
		jadx.load();

		for (JavaClass cls : jadx.getClassesWithInners()) {
			Map<String, JavaClass> extracted = extractAidlSubclasses(cls);
			if (isAidlClass(extracted)) {
				String writeableStr = getAidlWriteableString(cls, extracted);
				System.out.println(writeableStr);

				String outputPath;
				String service = isServiceManagerAidl(cls, serviceList);
				if (service != null) {
					outputPath = binderServiceAidlPath;
					++binderServiceAidlCount;
					if (isAccessibleService(service, accessibleServices)) {
						writeToFile(writeableStr, new File(romPath, accessibleAidlPath), true);
					}
				} else {
					outputPath = binderAnonymousAidlPath;
					++binderAnonymousAidlCount;
				}
				writeToFile(writeableStr, new File(romPath, outputPath), true);
			}
		}

		jadx.close();
	}

	private Map<String, JavaClass> extractAidlSubclasses(JavaClass cls) {
		Map<String, JavaClass> resultMap = new HashMap<>();
		List<JavaClass> innerClasses = cls.getInnerClasses();
		for (JavaClass innerClass : innerClasses) {
			if (innerClass.getName().equals("Default")) {
				resultMap.put(AIDL_DEFAULT, innerClass);
			} else if (innerClass.getName().equals("Stub")) {
				resultMap.put(AIDL_STUB, innerClass);
			}
			if (resultMap.containsKey(AIDL_DEFAULT) && resultMap.containsKey(AIDL_STUB))
				break;
		}
		if (resultMap.containsKey(AIDL_DEFAULT) && resultMap.containsKey(AIDL_STUB)) {
			List<JavaClass> stubInnerClasses = resultMap.get(AIDL_STUB).getInnerClasses();
			for (JavaClass innerClass : stubInnerClasses) {
				if (innerClass.getName().equals("Proxy")) {
					resultMap.put(AIDL_STUB_PROXY, innerClass);
					break;
				}
			}
		}
		return resultMap;
	}

	private boolean isAidlClass(Map<String, JavaClass> extracted) {
		return extracted.containsKey(AIDL_DEFAULT) &&
				extracted.containsKey(AIDL_STUB) &&
				extracted.containsKey(AIDL_STUB_PROXY);
	}

	private String isServiceManagerAidl(JavaClass cls, List<String> serviceList) {
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

	private boolean isAccessibleService(String service, List<String> accessibleServices) {
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

	private String getAidlWriteableString(JavaClass cls, Map<String, JavaClass> extracted) {
		StringBuilder sb = new StringBuilder();
		sb.append(cls.getFullName());
		sb.append('\n');
		List<String> aidlMethodList = new ArrayList<>();
		for (JavaMethod mth : cls.getMethods()) {
			aidlMethodList.add(mth.getName());
		}
		for (JavaMethod mth : extracted.get(AIDL_DEFAULT).getMethods()) {
			if (aidlMethodList.contains(mth.getName())) {
				sb.append(getAidlMethodString(mth));
				sb.append('\n');
			}
		}
		sb.append('\n');
		return sb.toString();
	}

	private void writeToFile(String writeableStr, File outputFile, boolean append) {
		try {
			FileWriter fw = new FileWriter(outputFile, append);
			fw.write(writeableStr);
			fw.flush();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	private String getAidlMethodString(JavaMethod method) {
		MethodNode methodNode = method.getMethodNode();
		return beautyMethodCodeStr(methodNode.getCodeStr());
	}

	private String beautyMethodCodeStr(String codeStr) {
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
		System.out.println(beautyCodeStr);
		return beautyCodeStr;
	}
}
