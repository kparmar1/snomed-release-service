package org.ihtsdo.buildcloud.service;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.UnsupportedEncodingException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.ihtsdo.buildcloud.dao.BuildDAO;
import org.ihtsdo.buildcloud.entity.Build;
import org.ihtsdo.buildcloud.service.build.RF2Constants;
import org.ihtsdo.buildcloud.service.build.transform.RepeatableRelationshipUUIDTransform;
import org.ihtsdo.buildcloud.service.build.transform.TransformationException;
import org.ihtsdo.buildcloud.service.helper.Type5UuidFactory;
import org.ihtsdo.otf.rest.exception.ProcessingException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;

import com.google.code.externalsorting.ExternalSort;

@Resource
public class RelationshipHelper {
	
	@Autowired
	private BuildDAO buildDAO;
	
	private static Type5UuidFactory type5UuidFactory;

	private static int MAX_GROUP_NUMBER = 20;

	private static final int GROUP_COLUMN_INDEX = 6;

	private static final String STATED_RELATIONSHIP = "_StatedRelationship_";

	private static final Logger LOGGER = LoggerFactory.getLogger(RelationshipHelper.class);

	public static Map<String, Deque<String>> buildUuidSctidMapFromPreviousRelationshipFile(String previousRelationshipFilePath)
			throws ProcessingException {
		try {
			Map<String, Deque<String>> uuidSctidMap = new HashMap<>();
			
			//We will sort the file by effectiveTime to ensure the most recently modified SCTIDS
			//end up at the top of the stack.  Fastest to copy the effective time to be the first field, so
			//SCTID will move to index 1.
			File sortedPreviousRelationshipFile = sortFile(previousRelationshipFilePath, 1);
			RepeatableRelationshipUUIDTransform relationshipUUIDTransform = new RepeatableRelationshipUUIDTransform();
			long sctIdCount = 0;
			try (BufferedReader reader = new BufferedReader(new InputStreamReader(new FileInputStream(sortedPreviousRelationshipFile)))) {
				String line;
				String[] columnValues;
				reader.readLine(); // Discard header
				while ((line = reader.readLine()) != null) {
					columnValues = line.split(RF2Constants.COLUMN_SEPARATOR, -1);
					String uuid = relationshipUUIDTransform.getCalculatedUuidFromRelationshipValues(columnValues);

					// Do we have a Stack for this UUID? Create if required, then push onto it.
					Deque<String> thisStack = uuidSctidMap.get(uuid);
					if (thisStack == null) {
						thisStack = new ArrayDeque<String>();
						uuidSctidMap.put(uuid, thisStack);
					}
					thisStack.push(columnValues[1]); // Will end up with newest SCTID for a given triple + group at top of stack
					sctIdCount++;
				}
			}
			sortedPreviousRelationshipFile.delete();
			LOGGER.debug("Stored {} SCTIDs in UUID map.", sctIdCount);
			return uuidSctidMap;
		} catch (IOException e) {
			throw new ProcessingException("Failed to read previous relationship file during id reconciliation - "
					+ previousRelationshipFilePath, e);
		} catch (NoSuchAlgorithmException e) {
			throw new ProcessingException("Failed to use previous relationship file during id reconciliation.", e);
		}
	}
	
	protected static File sortFile(String filePath, int sortOnColumnIdx) throws IOException {

		// First we're going to read through the whole file and copy the sorting column to the start of the line, so we don't have to do
		// an insane number of splits and compares when the external sorter runs.
		File prefixedFile = prefixLinesWithColumn(filePath, sortOnColumnIdx);

		// Now we can do a straight forward sort
		File sortedFile = new File(filePath + ".sorted");
		ExternalSort.sort(prefixedFile, sortedFile);
		return sortedFile;
	}

	protected static File prefixLinesWithColumn(String filePath, int columnToCopy) throws IOException {

		File tmpFile = File.createTempFile("relationship_sorting", ".tmp");

		String strLine;
		long lineCount = 0;
		try (Writer writer = new BufferedWriter(new OutputStreamWriter(new FileOutputStream(tmpFile), StandardCharsets.UTF_8));
				FileInputStream fstream = new FileInputStream(filePath);
				BufferedReader br = new BufferedReader(new InputStreamReader(fstream));) {
			while ((strLine = br.readLine()) != null) {
				String[] columns = strLine.split(RF2Constants.COLUMN_SEPARATOR);
				writer.write(columns[columnToCopy]);
				writer.write(RF2Constants.COLUMN_SEPARATOR);
				writer.write(strLine);
				writer.write(RF2Constants.LINE_ENDING);
				lineCount++;
			}
		}
		LOGGER.debug("Prefixed {} lines in {} prior to sorting", lineCount, tmpFile);
		return tmpFile;
	}

	public String getStatedRelationshipInputFile(Build build) {
		
		//get a list of input file names
		final List<String> inputfilesList = buildDAO.listInputFileNames(build);
		for (final String inputFileName : inputfilesList) { 
			if (inputFileName.contains(STATED_RELATIONSHIP)) {
				return inputFileName;
			}
		}
			
		return null;
	}

	public static String getNextUUID(String[] columnValues) throws TransformationException, NoSuchAlgorithmException,
			UnsupportedEncodingException {
		// Initialise if required
		if (type5UuidFactory == null) {
			type5UuidFactory = new Type5UuidFactory();
		}

		int nextGroup = Integer.parseInt(columnValues[GROUP_COLUMN_INDEX]);
		if (nextGroup > MAX_GROUP_NUMBER) {
			throw new TransformationException("Exceeded maximum group number (" + MAX_GROUP_NUMBER + ") while processing id "
					+ columnValues[0]);
		}

		return type5UuidFactory.get(columnValues[4] + columnValues[5] + columnValues[7] + Integer.toString(nextGroup)).toString();
	}
}
