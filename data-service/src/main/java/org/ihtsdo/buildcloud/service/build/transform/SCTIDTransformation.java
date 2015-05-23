package org.ihtsdo.buildcloud.service.build.transform;

import org.ihtsdo.buildcloud.service.RelationshipHelper;
import org.ihtsdo.buildcloud.service.helper.Type5UuidFactory;
import org.ihtsdo.idgen.ws.CreateSCTIDFaultException;
import org.ihtsdo.idgen.ws.CreateSCTIDListFaultException;

import java.io.UnsupportedEncodingException;
import java.rmi.RemoteException;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class SCTIDTransformation implements BatchLineTransformation {

	public static final String ID_GEN_MODULE_ID_PARAM = "1";
	
	private final CachedSctidFactory sctidFactory;

	private final int componentIdCol;
	private final int moduleIdCol;
	private final String partitionId;
	private List<Long> activeSCTIDsInUse;

	public SCTIDTransformation(int componentIdCol, int moduleIdCol, String partitionId, CachedSctidFactory sctidFactory) {
		this.sctidFactory = sctidFactory;
		this.componentIdCol = componentIdCol;
		this.moduleIdCol = moduleIdCol;
		this.partitionId = partitionId;
	}

	// Modifier that will allow us to look up an alternative SCTID (currently only for Relationships)
	// if the one returned is already active and in use
	public SCTIDTransformation(int componentIdCol, int moduleIdCol, String partitionId, CachedSctidFactory sctidFactory,
			List<Long> activeSCTIDsInUse) {
		this(componentIdCol, moduleIdCol, partitionId, sctidFactory);
		this.activeSCTIDsInUse = activeSCTIDsInUse; // For use with Relationships and predictable UUIDs only
	}

	public void transformLine(String[] columnValues) throws TransformationException {
		if (columnValues != null && columnValues.length > componentIdCol &&
				(columnValues[componentIdCol].contains("-"))) {

			String uuidString = columnValues[componentIdCol];
			// Replace with SCTID.
			try {
				String moduleId = columnValues[moduleIdCol];
				Long sctid = sctidFactory.getSCTID(uuidString, partitionId, moduleId);

				// Are we checking against a list of SCTIDs in use to ensure this one is unique?
				if (activeSCTIDsInUse != null) {
					boolean isAlreadyInUse = true;
					while (isAlreadyInUse) {
						isAlreadyInUse = activeSCTIDsInUse.contains(sctid);
						if (isAlreadyInUse) {
							// Increase the group number to get a new UUID and obtain an SCTID based on that
							uuidString = RelationshipHelper.getNextUUID(columnValues);
							sctid = sctidFactory.getSCTID(uuidString, partitionId, moduleId);
						} else {
							activeSCTIDsInUse.add(sctid);
						}
					}
				}

				columnValues[componentIdCol] = sctid.toString();
			} catch (CreateSCTIDFaultException | RemoteException | UnsupportedEncodingException | NoSuchAlgorithmException
					| InterruptedException e) {
				throw new TransformationException("SCTID creation request failed.", e);
			}
		}
	}


	@Override
	public void transformLines(List<String[]> columnValuesList) throws TransformationException {
		// Collect uuid strings
		List<String> uuidStrings = new ArrayList<>();
		String uuidString;
		for (String[] columnValues : columnValuesList) {
			uuidString = columnValues[componentIdCol];
			if (uuidString.contains("-")) {
				uuidStrings.add(uuidString);
			}
		}
		transformLineGroup(columnValuesList, uuidStrings);
	}

	public void transformLineGroup(List<String[]> columnValuesList, List<String> uuidStrings) throws TransformationException {
		try {
			Map<String, Long> sctiDs = sctidFactory.getSCTIDs(uuidStrings, partitionId, ID_GEN_MODULE_ID_PARAM);
			// Replace UUIDs with looked up SCTIDs
			for (String[] columnValuesReplace : columnValuesList) {
				String idString = columnValuesReplace[componentIdCol];
				if (idString.contains("-")) {
					Long aLong = sctiDs.get(idString);
					if (aLong != null) {
						columnValuesReplace[componentIdCol] = aLong.toString();
					} else {
						throw new TransformationException("No SCTID for UUID " + idString);
					}
				}
			}
		} catch (RemoteException | CreateSCTIDListFaultException | InterruptedException e) {
			throw new TransformationException("SCTID list creation request failed.", e);
		}
	}

}
