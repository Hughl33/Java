package com.OysterFinish.UniqueChecker;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

public class FileProcesser {
	private FileProcesser () {}
	
	public static String[] processFile(String file) throws IOException {
		BufferedReader in = new BufferedReader(new FileReader(file));
		String lines = in.readLine();
		for (int i = 1; i < 36; i++) {
			lines += in.readLine();
		}
		in.close();
		
		String[] text = lines.split(" ");
		
		return text;
	}
}
