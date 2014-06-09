package org.ihtsdo.buildcloud.dao.helper;

import org.ihtsdo.buildcloud.entity.Build;
import org.ihtsdo.buildcloud.entity.Execution;
import org.ihtsdo.buildcloud.entity.Package;

public class ExecutionS3PathHelper {

	public static final String SEPARATOR = "/";
	private static final String CONFIG_JSON = "configuration.json";
	private static final String STATUS_PREFIX = "status:";
	private static final String OUTPUT = "output/";
	private static final String OUTPUT_FILES = "output-files";
	private static final String INPUT_FILES = "input-files";
	private static final String BUILD_FILES = "build-files";
	private static final String MANIFEST = "manifest";

	public StringBuffer getBuildPath(Build build) {
		String releaseCenterBusinessKey = build.getProduct().getExtension().getReleaseCenter().getBusinessKey();
		StringBuffer path = new StringBuffer();
		path.append(releaseCenterBusinessKey);
		path.append(SEPARATOR);
		path.append(build.getCompositeKey());
		path.append(SEPARATOR);
		return path;
	}

	public String getPackageInputFilesPath(Package aPackage) {
		return getPackageFilesPathAsStringBuffer(aPackage, INPUT_FILES).toString();
	}

	public String getPackageInputFilePath(Package aPackage, String filename) {
		return getPackageFilesPathAsStringBuffer(aPackage, INPUT_FILES).append(filename).toString();
	}
	
	public String getPackageOutputFilePath(Package aPackage, String filename) {
		return getPackageFilesPathAsStringBuffer(aPackage, OUTPUT_FILES).append(filename).toString();
	}

	public StringBuffer getPackageManifestDirectoryPathPath(Package aPackage) {
		StringBuffer buildPath = getBuildPath(aPackage.getBuild());
		buildPath.append(BUILD_FILES).append(SEPARATOR).append(MANIFEST).append(SEPARATOR);
		return buildPath;
	}

	public StringBuffer getExecutionPath(Execution execution) {
		return getExecutionPath(execution.getBuild(), execution.getId());
	}

	public StringBuffer getExecutionInputFilesPath(Execution execution, Package aPackage) {
		String businessKey = aPackage.getBusinessKey();
		return getExecutionInputFilesPath(execution, businessKey);
	}

	public StringBuffer getExecutionInputFilesPath(Execution execution, String packageBusinessKey) {
		return getExecutionPath(execution.getBuild(), execution.getId()).append(INPUT_FILES).append(SEPARATOR).append(packageBusinessKey).append(SEPARATOR);
	}

	public String getExecutionInputFilePath(Execution execution, String packageBusinessKey, String inputFile) {
		return getExecutionInputFilesPath(execution, packageBusinessKey).append(inputFile).toString();
	}

	public StringBuffer getExecutionOutputFilesPath(Execution execution, String packageBusinessKey) {
		return getExecutionPath(execution.getBuild(), execution.getId()).append(OUTPUT_FILES).append(SEPARATOR).append(packageBusinessKey).append(SEPARATOR);
	}

	public String getExecutionOutputFilePath(Execution execution, String packageBusinessKey, String relativeFilePath) {
		return getExecutionOutputFilesPath(execution, packageBusinessKey).append(relativeFilePath).toString();
	}

	public StringBuffer getExecutionPath(Build build, String executionId) {
		StringBuffer path = getBuildPath(build);
		path.append(executionId);
		path.append(SEPARATOR);
		return path;
	}

	public StringBuffer getBuildScriptsPath(Execution execution) {
		return getExecutionPath(execution).append("build-scripts").append(SEPARATOR);
	}

	public String getConfigFilePath(Execution execution) {
		return getFilePath(execution, CONFIG_JSON);
	}

	public String getStatusFilePath(Execution execution, Execution.Status status) {
		return getExecutionPath(execution).append(STATUS_PREFIX).append(status.toString()).toString();
	}

	public String getOutputPath(Execution execution, String outputRelativePath) {
		return getFilePath(execution, OUTPUT);
	}

	public String getOutputFilePath(Execution execution, String outputRelativePath) {
		return getFilePath(execution, OUTPUT + outputRelativePath);
	}

	private StringBuffer getPackageFilesPathAsStringBuffer(Package aPackage, String directionModifier) {
		StringBuffer buildPath = getBuildPath(aPackage.getBuild());
		buildPath.append(BUILD_FILES).append(SEPARATOR).append(directionModifier).append(SEPARATOR).append(aPackage.getBusinessKey()).append(SEPARATOR);
		return buildPath;
	}

	private String getFilePath(Execution execution, String relativePath) {
		return getExecutionPath(execution).append(relativePath).toString();
	}
}
