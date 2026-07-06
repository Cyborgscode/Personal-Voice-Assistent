package io;

import io.Dos;

public class Keyring {

	private static Dos dos = new Dos();
	private static final String LABEL = "PVA";
	private static final String SCHEMA = "pva-mailbox";

	public static String getPassword(String commonname) {
		String result = dos.readPipe("secret-tool lookup " + SCHEMA + " \"" + escape(commonname) + "\"").trim();
		return result.isEmpty() ? null : result;
	}

	public static boolean setPassword(String commonname, String password) {
		String result = dos.readPipe("secret-tool store --label='" + LABEL + "' " + SCHEMA + " \"" + escape(commonname) + "\"", password).trim();
		return !result.startsWith("Exception:");
	}

	public static boolean deletePassword(String commonname) {
		dos.readPipe("secret-tool clear " + SCHEMA + " \"" + escape(commonname) + "\"");
		return true;
	}

	private static String escape(String s) {
		return s.replace("\"", "\\\"")
			.replace("\\", "\\\\")
			.replace("`", "\\`")
			.replace("$", "\\$");
	}
}
