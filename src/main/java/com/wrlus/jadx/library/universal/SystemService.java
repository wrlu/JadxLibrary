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
import java.util.List;
import java.util.stream.Stream;

public class SystemService implements LibraryEntry {
	private static final List<String> romPaths = new ArrayList<>();
	private static final String androidFrameworkPath = "/packages/android";
	private static final String serviceListPath = "/service_list.txt";
	private static final String accessibleServicesPath = "/accessible_services.txt";
	private static final String accessibleAidlPath = "/accessible_aidl.txt";
	private static final String binderServiceAidlPath = "/binder_service_aidl.txt";
	private static final String binderAnonymousAidlPath = "/binder_anonymous_aidl.txt";
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
			if (isAidlClass(cls)) {
				System.out.println(cls.getFullName());
				String outputPath;
				String service = isServiceManagerAidl(cls, serviceList);
				if (service != null) {
					outputPath = binderServiceAidlPath;
					++binderServiceAidlCount;
					if (isAccessibleService(service, accessibleServices)) {
						writeToFile(cls, new File(romPath, accessibleAidlPath), true);
					}
				} else {
					outputPath = binderAnonymousAidlPath;
					++binderAnonymousAidlCount;
				}
				writeToFile(cls, new File(romPath, outputPath), true);
			}
		}

		jadx.close();
	}

	private boolean isAidlClass(JavaClass cls) {
		boolean hasDefault = false;
		JavaClass stubCls = null;
		List<JavaClass> innerClasses = cls.getInnerClasses();
		for (JavaClass innerClass : innerClasses) {
			if (innerClass.getName().equals("Default")) {
				hasDefault = true;
			} else if (innerClass.getName().equals("Stub")) {
				stubCls = innerClass;
			}
			if (hasDefault && stubCls != null) {
				break;
			}
		}
		if (hasDefault && stubCls != null) {
			JavaClass proxyCls = null;
			List<JavaClass> stubInnerClasses = stubCls.getInnerClasses();
			for (JavaClass innerClass : stubInnerClasses) {
				if (innerClass.getName().equals("Proxy")) {
					proxyCls = innerClass;
					break;
				}
			}
			return proxyCls != null;
		}
		return false;
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

	private String getAidlMethodString(JavaMethod method) {
		MethodNode methodNode = method.getMethodNode();
		System.out.println(methodNode.getCodeStr());
		return methodNode.toString();
	}

	private void writeToFile(JavaClass cls, File outputFile, boolean append) {
		try {
			FileWriter fw = new FileWriter(outputFile, append);
			fw.write(cls.getFullName());
			fw.write('\n');
			for (JavaMethod mth : cls.getMethods()) {
				fw.write(getAidlMethodString(mth));
				fw.write('\n');
			}
			fw.flush();
			fw.close();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
