package org.ihtsdo.buildcloud.service.precondition;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.util.Date;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;

import org.ihtsdo.buildcloud.dao.BuildDAO;
import org.ihtsdo.buildcloud.dao.ExecutionDAO;
import org.ihtsdo.buildcloud.dao.InputFileDAO;
import org.ihtsdo.buildcloud.entity.Build;
import org.ihtsdo.buildcloud.entity.Execution;
import org.ihtsdo.buildcloud.entity.Package;
import org.ihtsdo.buildcloud.entity.PreConditionCheckReport;
import org.ihtsdo.buildcloud.entity.PreConditionCheckReport.State;
import org.ihtsdo.buildcloud.entity.helper.TestEntityGenerator;
import org.ihtsdo.buildcloud.service.InputFileService;
import org.ihtsdo.buildcloud.service.exception.ResourceNotFoundException;
import org.ihtsdo.buildcloud.test.TestUtils;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/test/testDataServiceContext.xml"})
@Transactional
public abstract class PreconditionCheckTest {

	@Autowired
	private BuildDAO buildDAO;

	@Autowired
	private ExecutionDAO executionDAO;
	
	@Autowired
	private InputFileDAO inputFileDAO;

	@Autowired
	private TestUtils testUtils;

	protected Build build;
	protected Execution execution = null;
	
	protected PreconditionManager manager;
	@Autowired
	protected InputFileService inputFileService;
	
	
	private static final Logger LOGGER = LoggerFactory.getLogger(PreconditionCheckTest.class);
	
	static int executionIdx = 0;
	
	@Before
	public void setup(){
		build = buildDAO.find(1L, TestEntityGenerator.TEST_USER);
	}
	
	protected void createNewExecution() {
		Date creationTime = new GregorianCalendar(2014, 1, 4, 10, 30, executionIdx++).getTime();
		execution = new Execution(creationTime, build);
		
		//Because we're working with a unit test, that execution will probably already exist on disk, so wipe
		testUtils.scrubExecution(execution);
		
		// Copy all files from Build input and manifest directory to Execution input and manifest directory
		executionDAO.copyAll(build, execution);
	}

	protected State runPreConditionCheck(Class<? extends PreconditionCheck> classUnderTest) throws InstantiationException, IllegalAccessException {
		
		//Do we need an execution?
		if (execution == null) {
			createNewExecution();
		}
		
		//Create a manager for this test
		Map<String, List<PreConditionCheckReport>> report = manager.runPreconditionChecks(execution);
		Assert.assertNotNull(report);

		List<PreConditionCheckReport> allPrechecks = report.get(TestEntityGenerator.packageNames[0][0][0]);		//For the "Snomed Release Package"
		PreConditionCheckReport testResult = allPrechecks.get(0);												//Get the first test run
		
		String testName = testResult.getPreConditionCheckName();
		Assert.assertEquals (classUnderTest.getSimpleName(), testName);
		
		//If it's a fail, we'll debug that message just for testing purposes
		if (State.PASS != testResult.getResult())
			LOGGER.warn ("Test {} Reported {}",testName,testResult.getMessage());
		return testResult.getResult();
	}
	
	protected void loadManifest(String filename) throws FileNotFoundException {
		for (Package pkg : build.getPackages()) {
			if (filename != null) {
				String testFilePath = getClass().getResource(filename).getFile();
				File testManifest = new File (testFilePath);
				inputFileDAO.putManifestFile(pkg, new FileInputStream(testManifest), testManifest.getName(), testManifest.length());
			} else {
				inputFileDAO.deleteManifest(pkg);
			}
		}
		
		//When we load a manifest, we need that copied over to a new execution
		createNewExecution();
	}
	
	/** call before loadManifest.
	 * @param filename
	 * @throws ResourceNotFoundException
	 * @throws IOException
	 */
	protected void addEmptyFileToInputDirectory(String filename) throws ResourceNotFoundException, IOException{
	    for( Package pkg : build.getPackages()){
	    File tempFile = File.createTempFile("testTemp",".txt");
	    try( InputStream inputStream = new FileInputStream(tempFile)){
		
		
		    inputFileService.putInputFile(build.getCompositeKey(), pkg.getBusinessKey(), 
			    inputStream, filename, 0L,  TestEntityGenerator.TEST_USER);
	    }
	    finally{
		tempFile.deleteOnExit();
	    }
	   }
	}
	
	protected void deleteFilesFromInputFileByPattern(String fileExtension) throws ResourceNotFoundException{
	    for( Package pkg : build.getPackages()){
		inputFileService.deleteFilesByPattern(build.getCompositeKey(), pkg.getBusinessKey(),
			    fileExtension,TestEntityGenerator.TEST_USER);
	    }
	}
	    
}