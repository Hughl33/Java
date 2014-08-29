package com.OysterFinish.UniqueChecker;

import java.io.IOException;
import java.util.ArrayList;

public class UniqueChecker {
	public static void main(String[] args) throws IOException {
		if (args.length != 1) {
			System.err.println("Please input the correct file.");
			return;
		}
		String[] text = FileProcesser.processFile(args[0]);
		ArrayList<String> dubs = new ArrayList<String>();
		for (int i = 0; i < text.length; i++) {
			for (int j = 0; j < text.length; j++) {
				if (text[i] == text[j] && i != j) dubs.add(text[j]);
			}
		}
		
		if (dubs.size() == 0) System.out.println("All Unique");
		for (int i = 0; i < dubs.size(); i++) {
			System.out.println(dubs.get(i));
		}
	}
}
