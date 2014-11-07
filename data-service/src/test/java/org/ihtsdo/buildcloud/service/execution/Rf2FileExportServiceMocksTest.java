package org.ihtsdo.buildcloud.service.execution;

import org.easymock.EasyMock;
import org.easymock.IMocksControl;
import org.ihtsdo.buildcloud.dao.ExecutionDAO;
import org.ihtsdo.buildcloud.dao.io.AsyncPipedStreamBean;
import org.ihtsdo.buildcloud.entity.*;
import org.ihtsdo.buildcloud.entity.Package;
import org.ihtsdo.buildcloud.test.StreamTestUtils;
import org.junit.Before;
import org.junit.Test;

import java.io.*;
import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

public class Rf2FileExportServiceMocksTest {

	// Relationship files
	private static final String REL_A_FULL = "sct2_Relationship_Full_INT_20140131.txt";
	private static final String REL_A_SNAP = "sct2_Relationship_Snapshot_INT_20140131.txt";
	private static final String REL_A_DELTA = "sct2_Relationship_Delta_INT_20140131.txt";
	private static final String REL_B_SNAP = "sct2_Relationship_Snapshot_INT_20140731.txt";
	private static final String REL_B_FULL = "sct2_Relationship_Full_INT_20140731.txt";
	private static final String REL_B_DELTA = "sct2_Relationship_Delta_INT_20140731.txt";
	public static final String PREVIOUS_PUBLISHED_FILE_NAME = "prev.zip";
	private Execution execution;
	private Package pkg;
	private IMocksControl mocksControl;
	private ExecutionDAO executionDAO;

	@Before
	public void setup() throws ParseException {
		Build build = new Build(1L, "Test");
		Product product = new Product("Test Product");
		Extension extension = new Extension("extension");
		extension.setReleaseCenter(new ReleaseCenter("Test Release Center", "Test"));
		product.setExtension(extension);
		build.setProduct(product);
		build.setEffectiveTime(new SimpleDateFormat("yyyyMMdd").parse("20140731"));
		execution = new Execution(new Date(), build);
		pkg = new Package("PK1");
		pkg.setBuild(build);
		pkg.setPreviousPublishedPackage(PREVIOUS_PUBLISHED_FILE_NAME);

		mocksControl = EasyMock.createControl();
		executionDAO = mocksControl.createMock(ExecutionDAO.class);
	}

	@Test
	public void testExportFullAndDeltaFromSnapshotFirstRelease() throws ReleaseFileGenerationException, IOException {
		EasyMock.expect(executionDAO.getOutputFileInputStream(execution, pkg, REL_A_SNAP)).andReturn(getClass().getResourceAsStream(REL_A_SNAP));

		File tempDeltaFile = getTempFile(REL_A_DELTA);
		AsyncPipedStreamBean asyncPipedDeltaStreamBean = getAsyncPipedStreamBean(tempDeltaFile);
		EasyMock.expect(executionDAO.getOutputFileOutputStream(execution, pkg.getBusinessKey(), REL_A_DELTA)).andReturn(asyncPipedDeltaStreamBean);

		File tempFullFile = getTempFile(REL_A_FULL);
		AsyncPipedStreamBean asyncPipedFullStreamBean = getAsyncPipedStreamBean(tempFullFile);
		EasyMock.expect(executionDAO.getOutputFileOutputStream(execution, pkg.getBusinessKey(), REL_A_FULL)).andReturn(asyncPipedFullStreamBean);

		mocksControl.replay();
		pkg.setFirstTimeRelease(true);
		new Rf2FileExportService(execution, pkg, executionDAO, null, 1).generateDeltaAndFullFromSnapshot(REL_A_SNAP);
		mocksControl.verify();

		try (InputStream expectedDeltaStream = getClass().getResourceAsStream(REL_A_DELTA);
			 FileInputStream actualDeltaStream = new FileInputStream(tempDeltaFile)) {
			StreamTestUtils.assertStreamsEqualLineByLine(expectedDeltaStream, actualDeltaStream);
		}
		try (InputStream expectedFullStream = getClass().getResourceAsStream(REL_A_FULL);
			 FileInputStream actualFullStream = new FileInputStream(tempFullFile)) {
			StreamTestUtils.assertStreamsEqualLineByLine(expectedFullStream, actualFullStream);
		}
	}

	@Test
	public void testExportFullAndDeltaFromSnapshotAndPrevFull() throws ReleaseFileGenerationException, IOException {
		EasyMock.expect(executionDAO.getOutputFileInputStream(execution, pkg, REL_B_SNAP)).andReturn(getClass().getResourceAsStream(REL_B_SNAP));

		File tempDeltaFile = getTempFile(REL_B_DELTA);
		AsyncPipedStreamBean asyncPipedDeltaStreamBean = getAsyncPipedStreamBean(tempDeltaFile);
		EasyMock.expect(executionDAO.getOutputFileOutputStream(execution, pkg.getBusinessKey(), REL_B_DELTA)).andReturn(asyncPipedDeltaStreamBean);

		EasyMock.expect(executionDAO.getPublishedFileArchiveEntry(execution.getBuild().getProduct(), REL_B_FULL, PREVIOUS_PUBLISHED_FILE_NAME)).andReturn(getClass().getResourceAsStream(REL_A_FULL));

		File tempFullFile = getTempFile(REL_B_FULL);
		AsyncPipedStreamBean asyncPipedFullStreamBean = getAsyncPipedStreamBean(tempFullFile);
		EasyMock.expect(executionDAO.getOutputFileOutputStream(execution, pkg.getBusinessKey(), REL_B_FULL)).andReturn(asyncPipedFullStreamBean);

		mocksControl.replay();
		pkg.setFirstTimeRelease(false);
		new Rf2FileExportService(execution, pkg, executionDAO, null, 1).generateDeltaAndFullFromSnapshot(REL_B_SNAP);
		mocksControl.verify();

		try (InputStream expectedDeltaStream = getClass().getResourceAsStream(REL_B_DELTA);
			 FileInputStream actualDeltaStream = new FileInputStream(tempDeltaFile)) {
			StreamTestUtils.assertStreamsEqualLineByLine(expectedDeltaStream, actualDeltaStream);
		}
		try (InputStream expectedFullStream = getClass().getResourceAsStream(REL_B_FULL);
			 FileInputStream actualFullStream = new FileInputStream(tempFullFile)) {
			StreamTestUtils.assertStreamsEqualLineByLine(expectedFullStream, actualFullStream);
		}
	}

	private File getTempFile(String filename) throws IOException {
		File tempFile = File.createTempFile(getClass().getName(), filename);
		tempFile.deleteOnExit();
		return tempFile;
	}

	private AsyncPipedStreamBean getAsyncPipedStreamBean(File tempFile) throws IOException {
		return new AsyncPipedStreamBean(new FileOutputStream(tempFile), new Future<String>() {
			@Override
			public boolean isDone() {
				return true;
			}

			@Override
			public String get() throws InterruptedException, ExecutionException {
				return null;
			}

			@Override
			public String get(long timeout, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
				return null;
			}

			@Override
			public boolean cancel(boolean mayInterruptIfRunning) {
				return false;
			}

			@Override
			public boolean isCancelled() {
				return false;
			}
		});
	}

}
