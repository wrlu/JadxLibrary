package com.wrlus.jadx.library.universal.utils;

import com.google.gson.annotations.SerializedName;
import jadx.api.JavaMethod;

public class AidlMethod {
	public transient String definition;
	@SerializedName("fullDefinition")
	public String fullDefinition;
	public transient String code;

	public static String getMethodDefinitionStr(JavaMethod method) {
		return method.toString().replace(method.getDeclaringClass().getClassNode().toString() + ".", "");
	}

	public static String getMethodFullDefinitionStr(JavaMethod method) {
		String codeStr = method.getMethodNode().getCodeStr();

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

	public static String getMethodCodeStr(JavaMethod method) {
		return method.getMethodNode().getCodeStr();
	}
}
