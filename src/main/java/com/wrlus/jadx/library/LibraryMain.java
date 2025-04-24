package com.wrlus.jadx.library;

import com.wrlus.jadx.library.universal.SystemService;

import java.util.ArrayList;
import java.util.List;

public class LibraryMain {
	private final static List<Class<? extends LibraryEntry>> entryList = new ArrayList<>();

	static {
		entryList.add(SystemService.class);
	}

	public static void main(String[] args) {
		for (Class<? extends LibraryEntry> entryName : entryList) {
			try {
				LibraryEntry entry = entryName.getConstructor(new Class<?>[0]).newInstance();
				entry.onLibraryLoaded(args);
			} catch (ReflectiveOperationException e) {
				e.printStackTrace();
			}
		}
	}
}
