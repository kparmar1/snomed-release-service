package com.google.code.externalsorting;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;
import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExternalSortTest {

	private static final Logger LOGGER = LoggerFactory.getLogger(ExternalSortTest.class);


	@Test
	public void test() throws IOException {
		final String testFile = getClass().getResource("file-to-sort.txt").getFile();
		// ExternalSort sorter = new ExternalSort();
		File output = File.createTempFile("temp-file-name", ".tmp");
		ExternalSort.sort(new File(testFile), output);

		// prove that lines in the file get larger as they go up.
		List<String> lines = Files.readAllLines(Paths.get(output.getAbsolutePath()), StandardCharsets.UTF_8);
		int lastNumber = 0;
		for (String line : lines) {
			String[] columns = line.split("\t");
			try {
				int thisNumber = Integer.parseInt(columns[0]);
				Assert.assertTrue("Number in file must be greater than previous line", thisNumber > lastNumber);
				lastNumber = thisNumber;
			} catch (NumberFormatException e) {
				LOGGER.debug("Skipping non numeric field: {}", columns[0]);
			}
		}
		output.delete();
	}

}
