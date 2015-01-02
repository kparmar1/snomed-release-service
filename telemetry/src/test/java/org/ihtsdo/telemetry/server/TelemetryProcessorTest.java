package org.ihtsdo.telemetry.server;

import com.amazonaws.services.s3.transfer.TransferManager;
import com.amazonaws.services.s3.transfer.Upload;
import com.amazonaws.services.s3.transfer.model.UploadResult;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.helpers.LogLog;
import org.easymock.Capture;
import org.easymock.EasyMock;
import org.easymock.IAnswer;
import org.easymock.MockType;
import org.easymock.internal.MocksControl;
import org.ihtsdo.telemetry.TestService;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.FileCopyUtils;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.Arrays;
import java.util.UUID;
import javax.jms.JMSException;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/test_telemetry_server_application_context.xml"})
@Transactional
public class TelemetryProcessorTest {

	private static TestBroker testBroker;

	@Autowired
	private TransferManager mockTransferManager;

	private MocksControl mocksControl;
	private Upload mockUpload;

	private File testStreamFile;
	private String streamFileName;
	private String streamFileDestination;
	private String streamS3Destination;

	private TestProcessor testProcessor;

	@BeforeClass
	public static void setupBroker() throws JMSException {
		TelemetryProcessorTest.testBroker = new TestBroker();
	}

	@Before
	public void setUp() throws Exception {
		LogLog.setInternalDebugging(true);
		LogLog.setQuietMode(false);

		UUID uniqueSuffix = UUID.randomUUID();
		streamFileName = "test_telemetry_stream_" + uniqueSuffix + ".txt";
		streamFileDestination = "file:///tmp/" + streamFileName;
		streamS3Destination = "s3://local.build.bucket/test_telemetry_stream_" + uniqueSuffix + ".txt";

		mocksControl = new MocksControl(MockType.DEFAULT);
		mockUpload = mocksControl.createMock(Upload.class);

		testStreamFile = new File("/tmp/" + streamFileName);
		testStreamFile.delete();

		testProcessor = new TestProcessor();
	}

	@Test
	public void testErrorDetection() throws IOException, InterruptedException {
		Logger logger = LoggerFactory.getLogger(TestService.class);

		try {
			throw new Exception("Simulating thrown Exception");
		} catch (Exception e) {
			logger.error("Correctly detected thrown exception.", e);
		}
	}

	@Test
	public void testAggregateEventsToFile() throws IOException, InterruptedException {
		testProcessor.doProcessing(streamFileDestination);
		// Wait for the aggregator to finish.
		Thread.sleep(1000);

		String capturedEventStream = replaceDates(fileToString(testStreamFile));
		Assert.assertNotNull(capturedEventStream);
		Assert.assertEquals("Line count", 3, capturedEventStream.split("\n").length);
		Assert.assertEquals("DATE INFO  org.ihtsdo.telemetry.server.TestProcessor.doProcessing - Start of event stream\n" +
				"DATE INFO  org.ihtsdo.telemetry.server.TestProcessor.doProcessing - Processing...\n" +
				"DATE INFO  org.ihtsdo.telemetry.server.TestProcessor.doProcessing - End of event stream\n",
				capturedEventStream);
	}

	@Test
	public void testAggregateEventsToS3() throws IOException, InterruptedException {
		// Set up mock expectations
		final Capture<File> fileCapture = new Capture<>();
		final BooleanHolder fileAssertionsRan = new BooleanHolder();
		EasyMock.expect(mockTransferManager.upload(EasyMock.eq("local.build.bucket"), EasyMock.eq(streamFileName), EasyMock.capture(fileCapture))).andReturn(mockUpload);
		EasyMock.expect(mockUpload.waitForUploadResult()).andAnswer(new IAnswer<UploadResult>() {
			@Override
			public UploadResult answer() throws Throwable {
				// Run temp file assertions before it's deleted
				File capturedFile = fileCapture.getValue();
				Assert.assertNotNull(capturedFile);
				String capturedEventStream = replaceDates(fileToString(capturedFile));
				Assert.assertNotNull(capturedEventStream);
				Assert.assertEquals("Line count", 3, capturedEventStream.split("\n").length);
				Assert.assertEquals("DATE INFO  org.ihtsdo.telemetry.server.TestProcessor.doProcessing - Start of event stream\n" +
						"DATE INFO  org.ihtsdo.telemetry.server.TestProcessor.doProcessing - Processing...\n" +
						"DATE INFO  org.ihtsdo.telemetry.server.TestProcessor.doProcessing - End of event stream\n",
						capturedEventStream);
				fileAssertionsRan.b = true;
				return null;
			}
		});

		EasyMock.reset();
		mocksControl.replay();
		EasyMock.replay(mockTransferManager); // This mock not part of the mocksControl

		// Perform test scenario
		testProcessor.doProcessing(streamS3Destination);
		// Wait for the aggregator to finish.
		Thread.sleep(1000);

		// Assert mock expectations
		mocksControl.verify();
		EasyMock.verify(mockTransferManager);
		Assert.assertTrue(fileAssertionsRan.b);
	}

	@Test
	public void testAggregateEventsToFileWithException() throws IOException, InterruptedException {
		testProcessor.doProcessingWithException(streamFileDestination);
		// Wait for the aggregator to finish.
		Thread.sleep(1000);

		String capturedEventStream = stripLineNumbersFromStackTrace(replaceDates(fileToString(testStreamFile)));

		// Grab first 8 lines. The lower part of the stack includes the container (Maven, IDE etc.) so should not be part of unit test.
		String capturedEventStreamFirstEightLines = StringUtils.join(Arrays.copyOfRange(capturedEventStream.split("\n"), 0, 8), "\n");

		String expected = "DATE INFO  org.ihtsdo.telemetry.server.TestProcessor.doProcessingWithException - Start of event stream\n" +
				"DATE INFO  org.ihtsdo.telemetry.server.TestProcessor.doProcessingWithException - Processing...\n" +
				"DATE ERROR org.ihtsdo.telemetry.server.TestProcessor.doProcessingWithException - User input is not a valid float: a\n" +
				"java.lang.NumberFormatException: For input string: \"a\"\n" +
				"\tat sun.misc.FloatingDecimal.readJavaFormatString(FloatingDecimal.java:LINE)\n" +
				"\tat java.lang.Float.parseFloat(Float.java:LINE)\n" +
				"\tat org.ihtsdo.telemetry.server.TestProcessor.doProcessingWithException(TestProcessor.java:LINE)\n" +
				"\tat org.ihtsdo.telemetry.server.TelemetryProcessorTest.testAggregateEventsToFileWithException(TelemetryProcessorTest.java:LINE)";

		Assert.assertEquals(expected, capturedEventStreamFirstEightLines);
	}

	private String stripLineNumbersFromStackTrace(String s) {
		return s.replaceAll(":[\\d]+", ":LINE");
	}

	private String replaceDates(String capturedEventStream) {
		return capturedEventStream.replaceAll("[\\d]{8}[^ ]* ", "DATE ");
	}

	private String fileToString(File file) throws IOException {
		return FileCopyUtils.copyToString(new FileReader(file));
	}

	@After
	public void tearDown() throws Exception {
		testStreamFile.delete();
	}

	@AfterClass
	public static void tearDownBroker() throws JMSException {
		testBroker.close();
	}

	private static final class BooleanHolder {
		boolean b = false;
	}
}
