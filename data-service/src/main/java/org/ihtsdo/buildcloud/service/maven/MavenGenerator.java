package org.ihtsdo.buildcloud.service.maven;

import com.github.jknack.handlebars.Handlebars;
import com.github.jknack.handlebars.Helper;
import com.github.jknack.handlebars.Template;
import com.github.jknack.handlebars.Options;

import org.codehaus.jackson.map.ObjectMapper;
import org.ihtsdo.buildcloud.entity.Build;
import org.ihtsdo.buildcloud.entity.InputFile;
import org.ihtsdo.buildcloud.entity.MavenArtifact;
import org.ihtsdo.buildcloud.entity.Package;
import org.ihtsdo.buildcloud.service.file.FileUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.util.FileCopyUtils;

import java.io.*;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;

public class MavenGenerator {

	@Autowired
	private ObjectMapper objectMapper;

	private final Handlebars handlebars;
	private final Template artifactPomHandlebars;
	private final Template buildPomHandlebars;
	private final Template packagePomHandlebars;
	private final Template commonGroupIdHandlebars;
	private final Template artifactGroupIdHandlebars;
	private final Template artifactArtifactIdHandlebars;

	private static final String POM_XML = "pom.xml";

	public MavenGenerator() throws IOException {
		handlebars = new Handlebars();
		
		handlebars.registerHelper("eachInMap", new Helper<Map<String,String>> () {
			public CharSequence apply(Map<String, String> map, Options options) {
				String result = "";
				if (map != null){
					result += map.size() > 0 ? "\n\t" : "";
					for (Map.Entry<String, String> entry : map.entrySet()){
						result += "\t<" + entry.getKey() + ">" + entry.getValue() + "</" + entry.getKey() + ">\n\t";
					}
				}
				return new Handlebars.SafeString (result);
			}			
		});

		buildPomHandlebars = handlebars.compile("build-pom");
		packagePomHandlebars = handlebars.compile("package-pom");
		artifactPomHandlebars = handlebars.compile("artifact-pom");
		commonGroupIdHandlebars = handlebars.compile("common-group-id");
		artifactGroupIdHandlebars = handlebars.compile("artifact-group-id");
		artifactArtifactIdHandlebars = handlebars.compile("artifact-artifact-id");
	}

	public void generateArtifactPom(MavenArtifact artifact, Writer writer) throws IOException {
		artifactPomHandlebars.apply(artifact, writer);
		writer.close();
	}

	/*
	 * @return A temporary directory containing the generated build scripts.
	 */
	public File generateBuildScripts(String executionConfigurationJson) throws IOException {
		Map<String, Object> executionMap = objectMapper.readValue(executionConfigurationJson, Map.class);
		executionMap.put("version", ((String) executionMap.get("creationTime")).replace(":", "-"));

		File tempDirectory = Files.createTempDirectory(getClass().getCanonicalName()).toFile();
		File parentPom = new File(tempDirectory, POM_XML);
		
		// Write the pom to it's newly minted location, filling in the build-pom template
		FileWriter fw = new FileWriter(parentPom);
		buildPomHandlebars.apply(executionMap, fw);
		fw.close();
		
		// Now work through the child packages creating directory and poms for each one.
		Map<String, Object> buildMap = (Map<String, Object>) executionMap.get("build");
		List<Map<String, Object>> packagesMap = (List<Map<String, Object>>) buildMap.get("packages");
		for (Map<String, Object> packageMap : packagesMap) {
			packageMap.put("execution", executionMap);
			generatePackagePom(packageMap, tempDirectory);
		}
		return tempDirectory;
	}
	
	private void generatePackagePom(Map<String, Object> aPackage, File tempDirectory)  throws IOException {
		// Create a sub directory based on the businessKey of the package
		String packageBusinessKey = (String) aPackage.get("id");
		File packageDir = FileUtils.createDirectoryOrThrow(new File(tempDirectory, packageBusinessKey));
		File packagePom = new File(packageDir, POM_XML);
		
		// Write the pom to it's newly minted location, filling in the package-pom template
		FileWriter fw = new FileWriter(packagePom);
		packagePomHandlebars.apply(aPackage, fw);
		fw.close();
		
		// We also need a copy of assembly.xml
		InputStream packageAssemblyStream = this.getClass().getResourceAsStream("/package_assembly.xml");
		if (packageAssemblyStream != null) {
			FileCopyUtils.copy(packageAssemblyStream, new FileOutputStream(new File(packageDir, "assembly.xml")));
		} else {
			throw new FileNotFoundException("Unable to read required resource package_assembly.xml");
		}
	}

	public void generateArtifactAndGroupId(InputFile inputFile) throws IOException {
		Package aPackage = inputFile.getPackage();
		Build build = aPackage.getBuild();

		StringWriter groupIdWriter = new StringWriter();
		artifactGroupIdHandlebars.apply(build, groupIdWriter);
		String groupId = groupIdWriter.toString();
		inputFile.setGroupId(groupId);

		StringWriter artifactIdWriter = new StringWriter();
		artifactArtifactIdHandlebars.apply(inputFile, artifactIdWriter);
		String artifactId = artifactIdWriter.toString();
		inputFile.setArtifactId(artifactId);
	}
	

}