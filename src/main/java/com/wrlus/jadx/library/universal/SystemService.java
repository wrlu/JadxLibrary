package com.wrlus.jadx.library.universal;

import com.wrlus.jadx.library.LibraryEntry;

import java.util.ArrayList;
import java.util.List;

public class SystemService implements LibraryEntry {
	private static final List<String> romPaths = new ArrayList<>();

	static {
//		romPaths.add("D:/Users/xiaolu/Firmware/Android/Google/shiba_beta-bp22.250325.007");
//		romPaths.add("D:/Users/xiaolu/Firmware/Android/Google/tokay-bp1a.250405.007");
//		romPaths.add("D:/Users/xiaolu/Firmware/Android/Huawei/BLK-AL00_104.2.0.191");
//		romPaths.add("D:/Users/xiaolu/Firmware/Android/Honor/ELI-AN00_9.0.0.165");
		romPaths.add("D:/Users/xiaolu/Firmware/Android/OPPO/PJV110_15_SP1A.210812.016_U.1cfadf7_1-c6eb");
//		romPaths.add("D:/Users/xiaolu/Firmware/Android/Vivo/PD2364_15_15.1.8.2.W10.V000L1");
//		romPaths.add("D:/Users/xiaolu/Firmware/Android/Xiaomi/vermeer_AQ3A.240912.001_OS2.0.102.0.VNKCNXM");
	}

	@Override
	public void onLibraryLoaded(String[] args) {
		if (args.length > 1) {
			processRom(args[1]);
			return;
		}
		for (String romPath : romPaths) {
			System.out.println("Process ROM: " + romPath);
			processRom(romPath);
		}
	}

	public static void processRom(String romPath) {
		RomProcessor processor = new RomProcessor(romPath);
		processor.initServiceList();
		processor.initAccessibleServiceList();

		long startTime = System.currentTimeMillis();
		processor.getDecompiler().initFramework();
		long endTime = System.currentTimeMillis();
		float duration = (float) (endTime - startTime) / 1000;
		System.out.println("initFramework time cost: " + duration + " second(s)");

		startTime = System.currentTimeMillis();
		processor.searchAidlDefinition();
		processor.searchAidlImpl();
		endTime = System.currentTimeMillis();
		duration = (float) (endTime - startTime) / 1000;
		System.out.println("search aidl time cost: " + duration + " second(s)");

		processor.dumpAidlToFile();
		processor.dumpAidlCodeToMarkdown();
		processor.dumpAidlCodeToFile();
	}
}
