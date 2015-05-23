package org.ihtsdo.buildcloud.service.build.transform;

import java.io.UnsupportedEncodingException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Map;

import org.ihtsdo.buildcloud.service.helper.Type5UuidFactory;

import com.sun.j3d.utils.scenegraph.io.UnsupportedUniverseException;

public class RelationshipIdentifierTransform implements LineTransformation {

	private static final int ID_COLUMN_INDEX = 0;
	private static final int GROUP_COLUMN_INDEX = 6;
	private static final int MAX_GROUP_NUM = 12; // We'll go above this only if the classifier does.
	private final Map<String, Deque<String>> matchToReplacementMap;
	private final List<String> activeSctIdsAlreadyInUse;

	private Type5UuidFactory type5UuidFactory;

	public RelationshipIdentifierTransform(Map<String, Deque<String>> matchToReplacementMap, List<String> activeSctIdsAlreadyInUse)
			throws NoSuchAlgorithmException {
		this.matchToReplacementMap = matchToReplacementMap;
		this.activeSctIdsAlreadyInUse = activeSctIdsAlreadyInUse;
		this.type5UuidFactory = new Type5UuidFactory();
	}

	@Override
	public void transformLine(String[] columnValues) throws TransformationException {
		try {
			// We can pinch an SCTID from any group with the same triple, as long as the id is not already in use
			// but start by looking at a UUID that's already for this group
			List<String> potentialUUIDs = getPotentialUUIDs(columnValues);

			// We should already have the predicted UUID for the current triple + group
			assert columnValues[ID_COLUMN_INDEX].equals(potentialUUIDs.get(0));
			boolean replacementMade = false;

			for (String thisPotentialUUID : potentialUUIDs) {
				if (matchToReplacementMap.containsKey(thisPotentialUUID)) {
					// We have a stack of potential values to use, check that they're not already in use before replacing
					Deque<String> thisStack = matchToReplacementMap.get(thisPotentialUUID);

					while (replacementMade == false && thisStack != null && !thisStack.isEmpty()) {
						String potentialReplacement = thisStack.pop();
						// Is this value already in use?
						if (!activeSctIdsAlreadyInUse.contains(potentialReplacement)) {
							columnValues[ID_COLUMN_INDEX] = potentialReplacement;
							activeSctIdsAlreadyInUse.add(potentialReplacement);
							// Now this SCTID might historically exist for multiple UUIDs as it has moved groups, so that's why we
							// need a list not based on tracking the UUID.
							replacementMade = true;
						}
					}
				}
				// No need to look at other UUIDs if we've made a match
				if (replacementMade) {
					break;
				}
			}
		} catch (UnsupportedEncodingException e) {
			throw new TransformationException("Encoding Exception while processing Relationship row", e);
		}
	}

	/**
	 * @return an array of UUIDs matching all potential groups for the current triple, with the current group as the first element
	 * @throws UnsupportedEncodingException
	 */
	public List<String> getPotentialUUIDs(String[] columnValues) throws UnsupportedEncodingException {
		List<String> potentialUUIDs = new ArrayList<String>();
		// We'll work up to whatever is the higher of max group or the current group number
		int currentGroup = Integer.parseInt(columnValues[GROUP_COLUMN_INDEX]);
		int highestGroup = currentGroup > MAX_GROUP_NUM ? currentGroup : MAX_GROUP_NUM;

		// Put the current group's UUID into the first slot before looping. Skip it in the loop.
		// sourceId + destinationId + typeId + relationshipGroup
		String currentUUID = type5UuidFactory.get(columnValues[4] + columnValues[5] + columnValues[7] + columnValues[6]).toString();
		potentialUUIDs.add(currentUUID);

		// Now loop through the other groups
		for (int groupNum = 0; groupNum <= highestGroup; groupNum++) {
			if (groupNum != currentGroup) {
				String thisGroupUUID = type5UuidFactory.get(
						columnValues[4] + columnValues[5] + columnValues[7] + Integer.toString(groupNum)).toString();
				potentialUUIDs.add(thisGroupUUID);
			}
		}
		return potentialUUIDs;
	}

	@Override
	public int getColumnIndex() {
		return -1;
	}

}
