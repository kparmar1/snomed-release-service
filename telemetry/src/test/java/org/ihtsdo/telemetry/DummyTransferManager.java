package org.ihtsdo.telemetry;

import com.amazonaws.AmazonClientException;
import com.amazonaws.AmazonServiceException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;

import java.io.File;

public class DummyTransferManager extends TransferManager {

	public DummyTransferManager(AWSCredentials credentials) {
		super(credentials);
	}

	@Override
	public Upload upload(String bucketName, String key, File file) throws AmazonServiceException, AmazonClientException {
		return null;
	}
}
