package org.ihtsdo.buildcloud.service;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.junit.Test;
import org.junit.Assert;

public class RelationshipHelperTest {

	@Test
	public void testSortFile() throws IOException {
		final String testFile = getClass().getResource("file-pre-sorting.txt").getFile();
		final String expectedResultsFile = getClass().getResource("expected-post-sort.txt").getFile();

		File output = null;
		try {
			final int EFFECTIVE_TIME_COLUMN = 1;
			output = RelationshipHelper.sortFile(testFile, EFFECTIVE_TIME_COLUMN);
	
			// Ensure all the lines produced are equal to our expected results
			List<String> actualLines = Files.readAllLines(Paths.get(output.getAbsolutePath()), StandardCharsets.UTF_8);
			List<String> expectedLines = Files.readAllLines(Paths.get(expectedResultsFile), StandardCharsets.UTF_8);
	
			Assert.assertEquals("File size must remain the same after sorting", expectedLines.size(), actualLines.size());
			// The sorted file will be left with the effective time copied as an additional first column.
	
			for (int lineNum = 0; lineNum < actualLines.size(); lineNum++) {
				Assert.assertEquals("Line " + lineNum + " not the same as expected after sorting", expectedLines.get(lineNum),
						actualLines.get(lineNum));
			}
		} finally {
			output.delete();
		}
		
	}

}
