package org.ihtsdo.buildcloud.service;

import static org.ihtsdo.buildcloud.service.build.RF2Constants.*;

import java.io.*;
import java.security.NoSuchAlgorithmException;
import java.text.Normalizer;
import java.text.Normalizer.Form;
import java.text.ParseException;
import java.util.*;
import java.util.concurrent.ExecutionException;
import java.util.regex.Pattern;

import javax.naming.ConfigurationException;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import javax.xml.bind.Unmarshaller;
import javax.xml.transform.stream.StreamSource;

import org.apache.log4j.MDC;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.ihtsdo.buildcloud.config.DailyBuildResourceConfig;
import org.ihtsdo.buildcloud.dao.BuildDAO;
import org.ihtsdo.buildcloud.dao.ProductDAO;
import org.ihtsdo.buildcloud.dao.io.AsyncPipedStreamBean;
import org.ihtsdo.buildcloud.entity.*;
import org.ihtsdo.buildcloud.entity.Build.Status;
import org.ihtsdo.buildcloud.entity.PreConditionCheckReport.State;
import org.ihtsdo.buildcloud.entity.helper.EntityHelper;
import org.ihtsdo.buildcloud.manifest.*;
import org.ihtsdo.buildcloud.releaseinformation.ConceptMini;
import org.ihtsdo.buildcloud.releaseinformation.ReleasePackageInformation;
import org.ihtsdo.buildcloud.service.build.DailyBuildRF2DeltaExtractor;
import org.ihtsdo.buildcloud.service.build.RF2Constants;
import org.ihtsdo.buildcloud.service.build.Rf2FileExportRunner;
import org.ihtsdo.buildcloud.service.build.Zipper;
import org.ihtsdo.buildcloud.service.build.readme.ReadmeGenerator;
import org.ihtsdo.buildcloud.service.build.transform.StreamingFileTransformation;
import org.ihtsdo.buildcloud.service.build.transform.TransformationException;
import org.ihtsdo.buildcloud.service.build.transform.TransformationFactory;
import org.ihtsdo.buildcloud.service.build.transform.TransformationService;
import org.ihtsdo.buildcloud.service.classifier.ClassificationResult;
import org.ihtsdo.buildcloud.service.file.ManifestXmlFileParser;
import org.ihtsdo.buildcloud.service.inputfile.prepare.ReportType;
import org.ihtsdo.buildcloud.service.inputfile.prepare.SourceFileProcessingReport;
import org.ihtsdo.buildcloud.service.postcondition.PostconditionManager;
import org.ihtsdo.buildcloud.service.precondition.ManifestFileListingHelper;
import org.ihtsdo.buildcloud.service.precondition.PreconditionManager;
import org.ihtsdo.buildcloud.service.rvf.RVFClient;
import org.ihtsdo.buildcloud.service.rvf.ValidationRequest;
import org.ihtsdo.buildcloud.service.security.SecurityHelper;
import org.ihtsdo.otf.resourcemanager.ResourceManager;
import org.ihtsdo.otf.rest.exception.BadConfigurationException;
import org.ihtsdo.otf.rest.exception.BusinessServiceException;
import org.ihtsdo.otf.rest.exception.EntityAlreadyExistsException;
import org.ihtsdo.otf.rest.exception.PostConditionException;
import org.ihtsdo.otf.rest.exception.ProcessingException;
import org.ihtsdo.otf.rest.exception.ResourceNotFoundException;
import org.ihtsdo.otf.utils.FileUtils;
import org.ihtsdo.snomed.util.rf2.schema.ComponentType;
import org.ihtsdo.snomed.util.rf2.schema.FileRecognitionException;
import org.ihtsdo.snomed.util.rf2.schema.SchemaFactory;
import org.ihtsdo.snomed.util.rf2.schema.TableSchema;
import org.ihtsdo.telemetry.client.TelemetryStream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.ResourceLoader;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.google.common.io.Files;

@Service
@Transactional
public class BuildServiceImpl implements BuildService {

	private static final String HYPHEN = "-";

	private static final String ADDITIONAL_RELATIONSHIP = "900000000000227009";
	
	private static final String STATED_RELATIONSHIP = "_StatedRelationship_";

	private static final Logger LOGGER = LoggerFactory.getLogger(BuildServiceImpl.class);

	@Autowired
	private BuildDAO dao;

	@Autowired
	private ProductDAO productDAO;

	@Autowired
	private PreconditionManager preconditionManager;

	@Autowired
	private PostconditionManager postconditionManager;

	@Autowired
	private ReadmeGenerator readmeGenerator;

	@Autowired
	private SchemaFactory schemaFactory;

	@Autowired
	private TransformationService transformationService;

	@Autowired
	private Integer fileProcessingFailureMaxRetry;

	@Autowired
	private String releaseValidationFrameworkUrl;

	@Autowired
	private Boolean offlineMode;

	@Autowired
	private Boolean localRvf;

	@Autowired
	private RF2ClassifierService rf2ClassifierService;
	
	@Autowired
	private String buildBucketName;
	
	@Autowired
	private DailyBuildResourceConfig dailyBuildResourceConfig;
	
	@Autowired
	private ResourceLoader cloudResourceLoader;
	
		
	@Override
	public Build createBuildFromProduct(final String releaseCenterKey, final String productKey) throws BusinessServiceException {
		final Date creationDate = new Date();
		final Product product = getProduct(releaseCenterKey, productKey);
		validateBuildConfig(product.getBuildConfiguration());
		Build build;
		try {
			synchronized (product) {
				// Do we already have an build for that date?
				final Build existingBuild = getBuild(product, creationDate);
				if (existingBuild != null) {
					throw new EntityAlreadyExistsException("An Build for product " + productKey + " already exists with build id " + existingBuild.getId());
				}
				build = new Build(creationDate, product);
				build.setProduct(product);
				build.setQaTestConfig(product.getQaTestConfig());
				// save build with config
				MDC.put(MDC_BUILD_KEY, build.getUniqueId());
				dao.save(build);
				LOGGER.info("Release build {} created for product {}", build.getId(), productKey);
				// Copy all files from Product input and manifest directory to Build input and manifest directory
				dao.copyAll(product, build);
				LOGGER.info("Input and manifest files are copied to build {}", build.getId());
			}
			if (!product.getBuildConfiguration().isJustPackage()) {
				// Perform Pre-condition testing
				final Status preStatus = build.getStatus();
				if (product.getBuildConfiguration().isInputFilesFixesRequired()) {
					// Removed try/catch If this is needed and fails, then we can't go further due to blank sctids
					doInputFileFixup(build);
				}
				final Status newStatus = runPreconditionChecks(build);
				dao.updatePreConditionCheckReport(build);
				if (newStatus != preStatus) {
					dao.updateStatus(build, newStatus);
				}
			}
		} catch (Exception e) {
			String msg = "Failed to create build for product " + productKey;
			LOGGER.error(msg, e);
			throw new BusinessServiceException(msg, e);
		} finally {
			MDC.remove(MDC_BUILD_KEY);
		}
		return build;
	}

	private void validateBuildConfig(BuildConfiguration buildConfiguration) throws BadConfigurationException {
		if (buildConfiguration.getEffectiveTime() == null) {
			throw new BadConfigurationException("The effective time must be set before a build is created.");
		}
		ExtensionConfig extensionConfig = buildConfiguration.getExtensionConfig();
		if (extensionConfig != null) {
			if (extensionConfig.getModuleId() == null || extensionConfig.getModuleId().isEmpty()) {
				throw new BadConfigurationException("The module id must be set for an extension build.");
			}
			if (extensionConfig.getNamespaceId() == null || extensionConfig.getNamespaceId().isEmpty()) {
				throw new BadConfigurationException("The namespace must be set for an extension build.");
			}
		}
	}

	private void doInputFileFixup(final Build build) throws IOException, TransformationException, NoSuchAlgorithmException, ProcessingException {
		// Due to design choices made in the terminology server, we may see input files with null SCTIDs in the
		// stated relationship file. These can be resolved as we would for the post-classified inferred relationship files
		// ie look up the previous file and if not found, try the IDGen Service using a predicted UUID
		LOGGER.debug("Performing fixup on input file prior to input file validation");
		final TransformationFactory transformationFactory = transformationService.getTransformationFactory(build);
		final String statedRelationshipInputFile = getStatedRelationshipInputFile(build);
		if (statedRelationshipInputFile == null) {
			LOGGER.debug("Stated Relationship Input Delta file not present for potential fix-up.");
			return;
		}
		InputStream statedRelationshipInputFileStream = dao.getInputFileStream(build, statedRelationshipInputFile);

		// We can't replace the file while we're reading it, so use a temp file
		final File tempDir = Files.createTempDir();
		final File tempFile = new File(tempDir, statedRelationshipInputFile);
		final FileOutputStream tempOutputStream = new FileOutputStream(tempFile);

		// We will not reconcile relationships with previous as that can lead to duplicate SCTIDs as triples may have historically moved
		// groups.

		final StreamingFileTransformation steamingFileTransformation = transformationFactory
				.getPreProcessFileTransformation(ComponentType.RELATIONSHIP);

		// Apply transformations
		steamingFileTransformation.transformFile(statedRelationshipInputFileStream, tempOutputStream, statedRelationshipInputFile,
				build.getBuildReport());

		// Overwrite the original file, and delete local temp copy
		dao.putInputFile(build, tempFile, false);
		tempFile.delete();
		tempDir.delete();
	}

	private String getStatedRelationshipInputFile(Build build) {
		//get a list of input file names
		final List<String> inputFilenames = dao.listInputFileNames(build);
		for (final String inputFileName : inputFilenames) {
			if (inputFileName.contains(STATED_RELATIONSHIP)) {
				return inputFileName;
			}
		}
		return null;
	}

	@Override
	public Build triggerBuild(final String releaseCenterKey, final String productKey, final String buildId, Integer failureExportMax) throws BusinessServiceException {
		// Start the build telemetry stream. All future logging on this thread and it's children will be captured.
		Build build;
		try {
			build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
			TelemetryStream.start(LOGGER, dao.getTelemetryBuildLogFilePath(build));

			try {
				dao.loadConfiguration(build);
			} catch (final IOException e) {
				String msg = String.format("Failed to load configuration for build %s", build.getId());
				LOGGER.error(msg, e);
				throw new BusinessServiceException(msg, e);
			}
			LOGGER.info("Trigger product", productKey, buildId);
			boolean isAbandoned = false;
			try (InputStream reportStream = dao.getBuildInputFilesPrepareReportStream(build)) {
				//check source file prepare report
				if (reportStream != null) {
					ObjectMapper objectMapper = new ObjectMapper();
					SourceFileProcessingReport sourceFilePrepareReport = objectMapper.readValue(reportStream, SourceFileProcessingReport.class);
					if (sourceFilePrepareReport.getDetails() != null && sourceFilePrepareReport.getDetails().containsKey(ReportType.ERROR)) {
						isAbandoned = true;
						updateStatusWithChecks(build, Status.FAILED_INPUT_PREPARE_REPORT_VALIDATION);
						LOGGER.error("Errors found in the source file prepare report therefore the build is abandoned. "
								+ "Please see detailed failures via the inputPrepareReport_url link listed.");
					}
				} else {
					LOGGER.warn("No source file prepare report found.");
				}
			} catch (IOException e) {
				updateStatusWithChecks(build, Status.FAILED_PRE_CONDITIONS);
				LOGGER.error("Failed to read source file processing report", e);
				isAbandoned = true;
			}
			// execute build
			if (!isAbandoned) {
				Status status = Status.BUILDING;
				final BuildReport report = build.getBuildReport();
				String resultStatus = "completed";
				String resultMessage = "Process completed successfully";
				try {
					updateStatusWithChecks(build, status);
					executeBuild(build, failureExportMax);
					status = Status.BUILT;
				} catch (final Exception e) {
					resultStatus = "fail";
					resultMessage = "Failure while processing build " + build.getUniqueId() + " due to: "
							+ e.getClass().getSimpleName() + (e.getMessage() != null ? " - " + e.getMessage() : "");
					LOGGER.error(resultMessage, e);
					status = Status.FAILED;
				}
				report.add("Progress Status", resultStatus);
				report.add("Message", resultMessage);
				dao.persistReport(build);
				updateStatusWithChecks(build, status);
			}
		} finally {
			// Finish the telemetry stream. Logging on this thread will no longer be captured.
			TelemetryStream.finish(LOGGER);
		}
		return build;
	}

	@Override
	public List<Build> findAllDesc(final String releaseCenterKey, final String productKey) throws ResourceNotFoundException {
		final Product product = getProduct(releaseCenterKey, productKey);
		if (product == null) {
			throw new ResourceNotFoundException("Unable to find product: " + productKey);
		}
		return dao.findAllDesc(product);
	}

	@Override
	public Build find(final String releaseCenterKey, final String productKey, final String buildId) throws ResourceNotFoundException {
		final Product product = getProduct(releaseCenterKey, productKey);
		if (product == null) {
			throw new ResourceNotFoundException("Unable to find product: " + productKey);
		}

		final Build build = dao.find(product, buildId);
		if (build == null) {
			throw new ResourceNotFoundException("Unable to find build: " + buildId + " for product: " + productKey);
		}

		build.setProduct(product);
		return build;
	}

	@Override
	public BuildConfiguration loadBuildConfiguration(final String releaseCenterKey, final String productKey, final String buildId) throws BusinessServiceException {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		try {
			dao.loadBuildConfiguration(build);
			return build.getConfiguration();
		} catch (final IOException e) {
			throw new BusinessServiceException("Failed to load configuration.", e);
		}
	}

	private void updateStatusWithChecks(final Build build, final Status newStatus) throws BadConfigurationException {
		// Assert status workflow position
		switch (newStatus) {
			case BUILDING :
				dao.assertStatus(build, Status.BEFORE_TRIGGER);
				break;
			case BUILT :
				dao.assertStatus(build, Status.BUILDING);
				break;
		}

		dao.updateStatus(build, newStatus);
	}

	@Override
	public InputStream getOutputFile(final String releaseCenterKey, final String productKey, final String buildId, final String outputFilePath) throws ResourceNotFoundException {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.getOutputFileStream(build, outputFilePath);
	}

	@Override
	public List<String> getOutputFilePaths(final String releaseCenterKey, final String productKey, final String buildId) throws BusinessServiceException {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.listOutputFilePaths(build);
	}

	@Override
	public InputStream getInputFile(final String releaseCenterKey, final String productKey, final String buildId, final String inputFilePath) throws ResourceNotFoundException {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.getInputFileStream(build, inputFilePath);
	}

	@Override
	public List<String> getInputFilePaths(final String releaseCenterKey, final String productKey, final String buildId) throws ResourceNotFoundException {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.listInputFileNames(build);
	}

	@Override
	public InputStream getLogFile(final String releaseCenterKey, final String productKey, final String buildId, final String logFileName) throws ResourceNotFoundException {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.getLogFileStream(build, logFileName);
	}

	@Override
	public List<String> getLogFilePaths(final String releaseCenterKey, final String productKey, final String buildId) throws ResourceNotFoundException {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.listBuildLogFilePaths(build);
	}

	private Build.Status runPreconditionChecks(final Build build) {
	    LOGGER.info("Start of Pre-condition checks");
	    Build.Status buildStatus = build.getStatus();
		final List<PreConditionCheckReport> preConditionReports = preconditionManager.runPreconditionChecks(build);
		build.setPreConditionCheckReports(preConditionReports);
		// analyze report to check whether there is fatal error for all packages
		for (final PreConditionCheckReport report : preConditionReports) {
			if (report.getResult() == State.FATAL) {
				// Need to alert release manager of fatal pre-condition check error.
				buildStatus = Status.FAILED_PRE_CONDITIONS;
				LOGGER.error("Fatal error occurred during pre-condition checks:{}, build {} will be halted.", report.toString(), build.getId());
				break;
			}
		}
		LOGGER.info("End of Pre-condition checks");
		return buildStatus;
	}

	private void executeBuild(final Build build, Integer failureExportMax) throws BusinessServiceException, NoSuchAlgorithmException, IOException {
		LOGGER.info("Start build {}", build.getUniqueId());
		checkManifestPresent(build);

		final BuildConfiguration configuration = build.getConfiguration();
		if (configuration.isJustPackage()) {
			copyFilesForJustPackaging(build);
		} else {
			final Map<String, TableSchema> inputFileSchemaMap = getInputFileSchemaMap(build);
			transformationService.transformFiles(build, inputFileSchemaMap);
			// Convert Delta input files to Full, Snapshot and Delta release files

			final Rf2FileExportRunner generator = new Rf2FileExportRunner(build, dao, fileProcessingFailureMaxRetry);

			if (!generator.isInferredRelationshipFileExist(rf2DeltaFilesSpecifiedByManifest(build))) {
				throw new BusinessServiceException("There is no inferred relationship delta file");
			}
			generator.generateReleaseFiles();
			
			//filter out additional relationships from the transformed delta
			String inferedDelta = getInferredDeltaFromInput(inputFileSchemaMap);
			if (inferedDelta != null) {
			 	String transformedDelta = inferedDelta.replace(INPUT_FILE_PREFIX, SCT2);
			 	transformedDelta = configuration.isBetaRelease() ? BuildConfiguration.BETA_PREFIX + transformedDelta : transformedDelta;
				retrieveAdditionalRelationshipsInputDelta(build, transformedDelta);
			}
			if (offlineMode) {
				LOGGER.info("Skipping inferred relationship creation because in Offline mode.");
			} else {
				// Run classifier
				//ClassificationResult result = rf2ClassifierService.classify(build, inputFileSchemaMap);
				//generator.generateRelationshipFiles(result);
			}
		}

		if (!offlineMode) {
			LOGGER.info("Start classification cross check");
			List<PostConditionCheckReport> reports  = postconditionManager.runPostconditionChecks(build);
			dao.updatePostConditionCheckReport(build, reports);
		}
	
		// Generate release package information
		if (configuration.getReleaseInformationFields() != null && !configuration.getReleaseInformationFields().isEmpty()) {
			generateReleasePackageFile(build,configuration.getReleaseInformationFields());
		}

		
		// Generate readme file
		generateReadmeFile(build);

		File zipPackage = null;
		try {
			try {
				final Zipper zipper = new Zipper(build, dao);
				zipPackage = zipper.createZipFile(false);
				LOGGER.info("Start: Upload zipPackage file {}", zipPackage.getName());
				dao.putOutputFile(build, zipPackage, true);
				LOGGER.info("Finish: Upload zipPackage file {}", zipPackage.getName());
				if (build.getConfiguration().isDailyBuild()) {
					DailyBuildRF2DeltaExtractor extractor = new DailyBuildRF2DeltaExtractor(build, dao);
					extractor.outputDailyBuildPackage(new ResourceManager(dailyBuildResourceConfig, cloudResourceLoader));
				}
			} catch (JAXBException | IOException | ResourceNotFoundException e) {
				throw new BusinessServiceException("Failure in Zip creation caused by " + e.getMessage(), e);
			} 

			String rvfStatus = "N/A";
			String rvfResultMsg = "RVF validation configured to not run.";
			if (!offlineMode || localRvf) {
				try {
					String s3ZipFilePath = dao.getOutputFilePath(build, zipPackage.getName());
					rvfResultMsg = runRVFPostConditionCheck(build, s3ZipFilePath, dao.getManifestFilePath(build), failureExportMax);
					if (rvfResultMsg == null) {
						rvfStatus = "Failed to run";
					} else {
						rvfStatus = "Completed";
					}
				} catch (final Exception e) {
					LOGGER.error("Failure during RVF Post Condition Testing", e);
					rvfStatus = "Processing Failed.";
					rvfResultMsg = "Failure due to: " + e.getLocalizedMessage();
				}
			}
			final BuildReport report = build.getBuildReport();
			report.add("post_validation_status", rvfStatus);
			report.add("rvf_response", rvfResultMsg);
			LOGGER.info("End of running build {}", build.getUniqueId());
		} finally {
			org.apache.commons.io.FileUtils.deleteQuietly(zipPackage);
		}
	}

	private void generateReleasePackageFile(Build build, String releaseInfoFields) throws BusinessServiceException {
		String[] fields = releaseInfoFields.split(",");
		BuildConfiguration buildConfig = build.getConfiguration();
		List<RefsetType> languagesRefsets = getLanguageRefsets(build);
		Map<String, Integer> deltaFromAndToDateMap = getDeltaFromAndToDate(build);
		Map<String, String> preferredTermMap = getPreferredTermMap(build);
		ReleasePackageInformation release = buildReleasePackageInformation(fields, buildConfig, languagesRefsets, deltaFromAndToDateMap, preferredTermMap);
		try {
			LOGGER.info("Generating release package information file for build {}", build.getUniqueId());
			final Unmarshaller unmarshaller = JAXBContext.newInstance(MANIFEST_CONTEXT_PATH).createUnmarshaller();
			final InputStream manifestStream = dao.getManifestStream(build);
			final ListingType manifestListing = unmarshaller.unmarshal(new StreamSource(manifestStream), ListingType.class).getValue();

			String releaseFilename = null;
			if (manifestListing != null) {
				final FolderType rootFolder = manifestListing.getFolder();
				if (rootFolder != null) {
					final List<FileType> files = rootFolder.getFile();
					for (final FileType file : files) {
						final String filename = file.getName();
						if (file.getName().startsWith(RELEASE_INFORMATION_FILENAME_PREFIX) && filename.endsWith(RELEASE_INFORMATION_FILENAME_EXTENSION)) {
							releaseFilename = filename;
							break;
						}
					}
				}
			} else {
				LOGGER.warn("Can not generate release package information file, manifest listing is null.");
			}
			if (releaseFilename != null) {
				File releasePackageInfor = null;
				try {
					releasePackageInfor =  new File(releaseFilename);
					ObjectMapper objectMapper = new ObjectMapper();
					objectMapper.enable(SerializationConfig.Feature.INDENT_OUTPUT);
					objectMapper.disable(SerializationConfig.Feature.WRITE_NULL_PROPERTIES);
					objectMapper.writerWithDefaultPrettyPrinter().writeValue(releasePackageInfor, release);
					dao.putOutputFile(build, releasePackageInfor);
				} finally {
					if (releasePackageInfor != null) {
						releasePackageInfor.delete();
					}
				}
			} else {
				LOGGER.warn("Can not generate release package file, no file found in manifest root directory starting with '{}' and ending with '{}'",
						RELEASE_INFORMATION_FILENAME_PREFIX, RELEASE_INFORMATION_FILENAME_EXTENSION);
			}
		} catch (IOException | JAXBException e) {
			throw new BusinessServiceException("Failed to generate release package information file.", e);
		}
	}

	private Map<String, String> getPreferredTermMap(Build build) {
		Map<String, String> result = new HashMap<>();
		if (build.getConfiguration().getConceptPreferredTerms() != null) {
			String[] conceptIdAndTerms =  build.getConfiguration().getConceptPreferredTerms().split(",");
			for (String conceptIdAndTerm : conceptIdAndTerms) {
				String[] arr = conceptIdAndTerm.split(Pattern.quote("|"));
				if (arr.length != 0) {
					result.put(arr[0].trim(), arr[1].trim());
				}
			}
		}
		return  result;
	}

	private ReleasePackageInformation buildReleasePackageInformation(String[] fields, BuildConfiguration buildConfig, List<RefsetType> languagesRefsets, Map<String, Integer> deltaFromAndToDateMap, Map<String, String> preferredTermMap) {
		ReleasePackageInformation release = new ReleasePackageInformation();
		for (String field : fields) {
			switch (field) {
				case "effectiveTime":
					release.setEffectiveTime(buildConfig.getEffectiveTime() != null ? RF2Constants.DATE_FORMAT.format(buildConfig.getEffectiveTime()) : null);
					break;
				case "deltaFromDate":
					Integer deltaFromDateInt = deltaFromAndToDateMap.get("deltaFromDate");
					release.setDeltaFromDate(deltaFromDateInt != null ? deltaFromDateInt.toString() : null);
					break;
				case "deltaToDate":
					Integer deltaToDateInt = deltaFromAndToDateMap.get("deltaToDate");
					release.setDeltaToDate(deltaToDateInt != null ? deltaToDateInt.toString() : null);
					break;
				case "includedModules":
					String extensionModule = buildConfig.getExtensionConfig() != null ? (buildConfig.getExtensionConfig().getModuleId() != null ? buildConfig.getExtensionConfig().getModuleId() : null) : null;
					if (extensionModule != null) {
						List<ConceptMini> list = new ArrayList<>();
						for (String moduleId : extensionModule.split(",")) {
							ConceptMini conceptMini = new ConceptMini();
							String moudleId = String.valueOf(moduleId).trim();
							conceptMini.setId(moudleId);
							conceptMini.setTerm(preferredTermMap.containsKey(moudleId) ? preferredTermMap.get(moudleId) : "");
							list.add(conceptMini);
						}
						release.setIncludedModules(list);
					}
					break;
				case "languageRefsets":
					List<ConceptMini> list = new ArrayList<>();
					for (RefsetType refsetType : languagesRefsets) {
						ConceptMini conceptMini = new ConceptMini();
						String languageRefsetId = String.valueOf(refsetType.getId()).trim();
						conceptMini.setId(languageRefsetId);
						conceptMini.setTerm(preferredTermMap.containsKey(languageRefsetId) ? preferredTermMap.get(languageRefsetId) : refsetType.getLabel());
						list.add(conceptMini);
					}
					release.setLanguageRefsets(list);
					break;
				case "licenceStatement":
					release.setLicenceStatement(buildConfig.getLicenceStatement());
					break;
			}
		}

		return release;
	}

	private List<RefsetType> getLanguageRefsets(Build build) {
		List<RefsetType> languagesRefsets = new ArrayList<>();
		try (InputStream manifestInputSteam = dao.getManifestStream(build)) {
			final ManifestXmlFileParser parser = new ManifestXmlFileParser();
			final ListingType listingType = parser.parse(manifestInputSteam);
			FolderType folderType = listingType.getFolder();
			List<FolderType>  folderTypes = folderType.getFolder();
			for (FolderType subFolderType1 : folderTypes) {
				if (subFolderType1.getName().equalsIgnoreCase(DELTA)) {
					for (FolderType subFolderType2 : subFolderType1.getFolder()) {
						if (subFolderType2.getName().equalsIgnoreCase(REFSET)) {
							for (FolderType subFolderType3 : subFolderType2.getFolder()) {
								if (subFolderType3.getName().equalsIgnoreCase(LANGUAGE)) {
									List<FileType> fileTypes = subFolderType3.getFile();
									for (FileType fileType : fileTypes) {
										ContainsReferenceSetsType refset = fileType.getContainsReferenceSets();
										for (RefsetType refsetType : refset.getRefset()) {
											languagesRefsets.add(refsetType);
										}
									}
									break;
								}
							}
							break;
						}
					}
					break;
				}
			}
		} catch (ResourceNotFoundException | JAXBException | IOException e) {
			LOGGER.error("Failed to parse manifest xml file." + e.getMessage());
		}

		return languagesRefsets;
	}

	private Map<String, Integer> getDeltaFromAndToDate(Build build) {
		Map<String, Integer> result = new HashMap<>();
		BuildConfiguration configuration = build.getConfiguration();
		String previousReleaseDateStr = null;
		if (configuration.getPreviousPublishedPackage() != null && !configuration.getPreviousPublishedPackage().isEmpty()) {
			String[] tokens = build.getConfiguration().getPreviousPublishedPackage().split(RF2Constants.FILE_NAME_SEPARATOR);
			if (tokens.length > 0) {
				previousReleaseDateStr = tokens[tokens.length - 1].replace(RF2Constants.ZIP_FILE_EXTENSION, "");
				try {
					Date preReleasedDate = RF2Constants.DATE_FORMAT.parse(previousReleaseDateStr);
					previousReleaseDateStr = RF2Constants.DATE_FORMAT.format(preReleasedDate); // make sure the date in format yyyyMMdd
				} catch (ParseException e) {
					LOGGER.error("Expecting release date format in package file name to be yyyyMMdd");
					previousReleaseDateStr = null;
				}
			}
		}

		result.put("deltaFromDate", previousReleaseDateStr != null ? Integer.valueOf(previousReleaseDateStr) : null);
		result.put("deltaToDate", Integer.valueOf(RF2Constants.DATE_FORMAT.format(configuration.getEffectiveTime())));

		return result;
	}

	/** Manifest.xml can have delta, snapshot or Full only and all three combined.
	 * 
	 * @param build
	 * @return
	 */
	private List<String> rf2DeltaFilesSpecifiedByManifest(Build build) {
		List<String> result =  new ArrayList<>();
		try (InputStream manifestInputSteam = dao.getManifestStream(build)) {
			final ManifestXmlFileParser parser = new ManifestXmlFileParser();
			final ListingType listingType = parser.parse(manifestInputSteam);
			Set<String> filesRequested = new HashSet<>();
			for ( String fileName : ManifestFileListingHelper.listAllFiles(listingType)) {
				if (fileName != null && fileName.endsWith(TXT_FILE_EXTENSION)) {
					if (fileName.contains(DELTA + FILE_NAME_SEPARATOR) || fileName.contains(DELTA + HYPHEN) ) {
						filesRequested.add(fileName);
					} else if (fileName.contains(SNAPSHOT + FILE_NAME_SEPARATOR) || fileName.contains(SNAPSHOT + HYPHEN) ) {
						filesRequested.add(fileName.replace(SNAPSHOT, DELTA));
					} else if (fileName.contains(FULL + FILE_NAME_SEPARATOR) || fileName.contains(FULL + HYPHEN) ) {
						filesRequested.add(fileName.replace(FULL, DELTA));
					}
				}
			}
			//changed to rel2 input files format
			for (String delta : filesRequested) {
				String[] splits = delta.split(FILE_NAME_SEPARATOR);
				splits[0] = INPUT_FILE_PREFIX;
				StringBuilder relFileBuilder = new StringBuilder();
				for (int i=0; i< splits.length; i++ ) {
					if (i > 0) {
						relFileBuilder.append(FILE_NAME_SEPARATOR);
					}
					relFileBuilder.append(splits[i]);
				}
				String relFileName = relFileBuilder.toString();
				if (!Normalizer.isNormalized(relFileName,Form.NFC)) {
					relFileName = Normalizer.normalize(relFileBuilder.toString(),Form.NFC);
				}
				result.add(relFileName);
			}
		} catch (ResourceNotFoundException | JAXBException | IOException e) {
			LOGGER.error("Failed to parse manifest xml file." + e.getMessage());
		} 
		return result;
	}

	private void retrieveAdditionalRelationshipsInputDelta(final Build build, String inferedDelta) throws BusinessServiceException {
		LOGGER.debug("Retrieving inactive additional relationship from transformed delta:" + inferedDelta);
		String originalDelta = inferedDelta + "_original";
		String additionalRelsDelta = inferedDelta.replace(RF2Constants.TXT_FILE_EXTENSION, RF2Constants.ADDITIONAL_TXT);
		dao.renameTransformedFile(build, inferedDelta, originalDelta, false);
		try (final OutputStream outputStream = dao.getTransformedFileOutputStream(build, additionalRelsDelta).getOutputStream();
		BufferedWriter writer = new BufferedWriter(new OutputStreamWriter(outputStream, UTF_8))) {
		final InputStream inputStream = dao.getTransformedFileAsInputStream(build, originalDelta);
		if (inputStream != null) {
			try (final BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream))) {
				String line;
				boolean isFirstLine = true;
				while ((line = reader.readLine()) != null) {
					if (isFirstLine){
						writer.write(line);
						writer.write(LINE_ENDING);
						isFirstLine = false;
					}
					String[] columnValues = line.split(COLUMN_SEPARATOR);
					if (ADDITIONAL_RELATIONSHIP.equals(columnValues[8]) && BOOLEAN_FALSE.equals(columnValues[2])) {
						writer.write(line);
						writer.write(LINE_ENDING);
					}
				}
			}
		}
		} catch (final IOException e) {
			throw new BusinessServiceException("Error occurred when reading original relationship delta transformed file:" + originalDelta, e);
		}
	}

	private String getInferredDeltaFromInput(final Map<String, TableSchema> inputFileSchemaMap) {
		for (final String inputFilename : inputFileSchemaMap.keySet()) {
			final TableSchema inputFileSchema = inputFileSchemaMap.get(inputFilename);

			if (inputFileSchema == null) {
				continue;
			}

			if (inputFileSchema.getComponentType() == ComponentType.RELATIONSHIP) {
				return inputFilename;
			}
		}
			return null;
	}

	private void checkManifestPresent(final Build build) throws BusinessServiceException {
		try {
			final InputStream manifestStream = dao.getManifestStream(build);
			if (manifestStream == null) {
				throw new BadConfigurationException("Failed to find valid manifest file.");
			} else {
				manifestStream.close();
			}
		} catch (final IOException e) {
			throw new BusinessServiceException("Failed to close manifest file.", e);
		}
	}

	private String runRVFPostConditionCheck(final Build build, final String s3ZipFilePath, String manifestFileS3Path, Integer failureExportMax) throws IOException,
			PostConditionException, ConfigurationException {
		LOGGER.info("Initiating RVF post-condition check for zip file {} with failureExportMax param value {}", s3ZipFilePath,  failureExportMax);
		try (RVFClient rvfClient = new RVFClient(releaseValidationFrameworkUrl)) {
			final QATestConfig qaTestConfig = build.getQaTestConfig();
			// Has the client told us where to tell the RVF to store the results? Set if not
			if (qaTestConfig.getStorageLocation() == null || qaTestConfig.getStorageLocation().length() == 0) {
				final String storageLocation = build.getProduct().getReleaseCenter().getBusinessKey() 
						+ "/" + build.getProduct().getBusinessKey()
						+ "/" + build.getId();
				qaTestConfig.setStorageLocation(storageLocation);
			}
			BuildConfiguration buildConfiguration = build.getConfiguration();
			validateQaTestConfig(qaTestConfig, buildConfiguration);
			String effectiveTime = buildConfiguration.getEffectiveTimeFormatted();
			boolean releaseAsAnEdition = false;
			String includedModuleId = null;
			ExtensionConfig extensionConfig = buildConfiguration.getExtensionConfig();
			if (extensionConfig != null) {
				releaseAsAnEdition = extensionConfig.isReleaseAsAnEdition();
				includedModuleId = extensionConfig.getModuleId();

			}
			String runId = Long.toString(System.currentTimeMillis());
			ValidationRequest request = new ValidationRequest(runId);
			request.setBuildBucketName(buildBucketName);
			request.setReleaseZipFileS3Path(s3ZipFilePath);
			request.setEffectiveTime(effectiveTime);
			request.setFailureExportMax(failureExportMax);
			request.setManifestFileS3Path(manifestFileS3Path);
			request.setReleaseAsAnEdition(releaseAsAnEdition);
			request.setIncludedModuleId(includedModuleId);
			return rvfClient.validateOutputPackageFromS3(qaTestConfig, request);
		}
	}

	private void validateQaTestConfig(final QATestConfig qaTestConfig, BuildConfiguration buildConfig) throws ConfigurationException {
		if (qaTestConfig == null || qaTestConfig.getAssertionGroupNames() == null) {
			throw new ConfigurationException("No QA test configured. Please check the assertion group name is specifield.");
		}
		if (!buildConfig.isJustPackage() && !buildConfig.isFirstTimeRelease()) {
			if (buildConfig.getExtensionConfig() == null && qaTestConfig.getPreviousInternationalRelease() == null) {
				throw new ConfigurationException("No previous international release is configured for non-first time release.");
			}
			if (qaTestConfig.getPreviousExtensionRelease() != null && qaTestConfig.getExtensionDependencyRelease() == null) {
				if (buildConfig.getExtensionConfig().isReleaseAsAnEdition()) {
					LOGGER.warn("This edition does not have dependency release. Empty dependency release will be used for testing");
				} else {
					throw new ConfigurationException("No extention dependency release is configured for extension testing.");
				}
			}
			
			if (qaTestConfig.getExtensionDependencyRelease() != null && qaTestConfig.getPreviousExtensionRelease() == null) {
				throw new ConfigurationException("Extension dependency release is specified but no previous extension release is configured for non-first time release testing.");
			}
		}
	}

	private void copyFilesForJustPackaging(final Build build) {
		LOGGER.info("Just copying files in build {} for packaging", build.getUniqueId());

		// Iterate each build input file
		final List<String> buildInputFilePaths = dao.listInputFileNames(build);
		for (final String relativeFilePath : buildInputFilePaths) {
			dao.copyInputFileToOutputFile(build, relativeFilePath);
		}
	}

	private Map<String, TableSchema> getInputFileSchemaMap(final Build build) throws BusinessServiceException {
		final List<String> buildInputFilePaths = dao.listInputFileNames(build);
		List<String> rf2DeltaFilesFromManifest = rf2DeltaFilesSpecifiedByManifest(build);
		for (String fileInManifest : rf2DeltaFilesFromManifest) {
			LOGGER.debug(fileInManifest);
		}
		final Map<String, TableSchema> inputFileSchemaMap = new HashMap<>();
		for (final String buildInputFilePath : buildInputFilePaths) {
			final TableSchema schemaBean;
			try {
				String filename = FileUtils.getFilenameFromPath(buildInputFilePath);
				if (!Normalizer.isNormalized(filename,Form.NFC)) {
					filename = Normalizer.normalize(filename,Form.NFC);
				}
				//Filtered out any files not required by Manifest.xml
				if (rf2DeltaFilesFromManifest.contains(filename)) {
					schemaBean = schemaFactory.createSchemaBean(filename);
					inputFileSchemaMap.put(buildInputFilePath, schemaBean);
				} else {
					LOGGER.info("RF2 file name:" + filename + " has not been specified in the manifest.xml");
				}
			} catch (final FileRecognitionException e) {
				throw new BusinessServiceException("Did not recognise input file '" + buildInputFilePath + "'", e);
			}
		}
		return inputFileSchemaMap;
	}

	private Build getBuildOrThrow(final String releaseCenterKey, final String productKey, final String buildId) throws ResourceNotFoundException {
		final Build build = find(releaseCenterKey, productKey, buildId);
		if (build == null) {
			throw new ResourceNotFoundException("Unable to find build for releaseCenterKey: " + releaseCenterKey + ", productKey: " + productKey + ", buildId: " + buildId);
		}
		return build;
	}

	private Build getBuild(final Product product, final Date creationTime) {
		return dao.find(product, EntityHelper.formatAsIsoDateTime(creationTime));
	}

	private Product getProduct(final String releaseCenterKey, final String productKey) throws ResourceNotFoundException {
		return productDAO.find(releaseCenterKey, productKey, SecurityHelper.getRequiredUser());
	}

	private void generateReadmeFile(final Build build) throws BusinessServiceException {
		try {
			LOGGER.info("Generating readMe file for build {}", build.getUniqueId());
			final Unmarshaller unmarshaller = JAXBContext.newInstance(MANIFEST_CONTEXT_PATH).createUnmarshaller();
			final InputStream manifestStream = dao.getManifestStream(build);
			final ListingType manifestListing = unmarshaller.unmarshal(new StreamSource(manifestStream), ListingType.class).getValue();

			String readmeFilename = null;
			if (manifestListing != null) {
				final FolderType rootFolder = manifestListing.getFolder();
				if (rootFolder != null) {
					final List<FileType> files = rootFolder.getFile();
					for (final FileType file : files) {
						final String filename = file.getName();
						if (file.getName().startsWith(README_FILENAME_PREFIX) && filename.endsWith(README_FILENAME_EXTENSION)) {
							readmeFilename = filename;
							break;
						}
					}
				}
			} else {
				LOGGER.warn("Can not generate readme, manifest listing is null.");
			}
			if (readmeFilename != null) {
				final AsyncPipedStreamBean asyncPipedStreamBean = dao.getOutputFileOutputStream(build, readmeFilename);
				try (OutputStream readmeOutputStream = asyncPipedStreamBean.getOutputStream()) {
					final BuildConfiguration configuration = build.getConfiguration();
					readmeGenerator.generate(configuration.getReadmeHeader(), configuration.getReadmeEndDate(), manifestListing, readmeOutputStream);
					asyncPipedStreamBean.waitForFinish();
				}
			} else {
				LOGGER.warn("Can not generate readme, no file found in manifest root directory starting with '{}' and ending with '{}'",
						README_FILENAME_PREFIX, README_FILENAME_EXTENSION);
			}
		} catch (IOException | InterruptedException | ExecutionException | JAXBException e) {
			throw new BusinessServiceException("Failed to generate readme file.", e);
		}
	}

	public void setFileProcessingFailureMaxRetry(final Integer fileProcessingFailureMaxRetry) {
		this.fileProcessingFailureMaxRetry = fileProcessingFailureMaxRetry;
	}

	@Override
	public QATestConfig loadQATestConfig(final String releaseCenterKey, final String productKey, final String buildId) throws BusinessServiceException {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		try {
			dao.loadQaTestConfig(build);
			return build.getQaTestConfig();
		} catch (final IOException e) {
			throw new BusinessServiceException("Failed to load QA test configuration.", e);
		}
	}

	@Override
	public InputStream getBuildReportFile(String releaseCenterKey,String productKey, String buildId) throws ResourceNotFoundException {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.getBuildReportFileStream(build);
	}

	@Override
	public InputStream getBuildInputFilesPrepareReport(String releaseCenterKey, String productKey, String buildId) {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.getBuildInputFilesPrepareReportStream(build);
	}

	@Override
	public InputStream getPreConditionChecksReport(String releaseCenterKey, String productKey, String buildId) {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.getPreConditionCheckReportStream(build);
	}

	@Override
	public InputStream getPostConditionChecksReport(String releaseCenterKey, String productKey, String buildId) {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.getPostConditionCheckReportStream(build);
	}

	@Override
	public List<String> getClassificationResultOutputFilePaths(String releaseCenterKey, String productKey, String buildId) {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.listClassificationResultOutputFileNames(build);
	}

	@Override
	public InputStream getClassificationResultOutputFile(String releaseCenterKey, String productKey, String buildId, String inputFilePath) throws ResourceNotFoundException {
		final Build build = getBuildOrThrow(releaseCenterKey, productKey, buildId);
		return dao.getClassificationResultOutputFileStream(build, inputFilePath);
	}
}
