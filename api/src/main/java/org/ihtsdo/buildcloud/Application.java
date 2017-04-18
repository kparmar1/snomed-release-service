package org.ihtsdo.buildcloud;

import com.mangofactory.swagger.configuration.SpringSwaggerConfig;
import com.mangofactory.swagger.models.dto.ApiInfo;
import com.mangofactory.swagger.paths.RelativeSwaggerPathProvider;
import com.mangofactory.swagger.plugin.EnableSwagger;
import com.mangofactory.swagger.plugin.SwaggerSpringMvcPlugin;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.ImportResource;
import org.springframework.jms.annotation.EnableJms;

@SpringBootApplication
@EnableJms
@EnableSwagger
@ImportResource("classpath:swaggerContext.xml")
public class Application {

	private RelativeSwaggerPathProvider pathProvider;

	public static void main(String[] args) {
		SpringApplication.run(Application.class, args);
	}

	@Bean
	public SwaggerSpringMvcPlugin apiImplementation(@Autowired SpringSwaggerConfig swaggerConfig){
		return new SwaggerSpringMvcPlugin(swaggerConfig)
				.apiInfo(apiInfo())
				.pathProvider(this.pathProvider);

	}

	private ApiInfo apiInfo() {
		return new ApiInfo(
				"SRS API Docs",
				"This is a listing of available apis of SNOMED release service. For more technical details visit "
						+ "<a src='https://github.com/IHTSDO/snomed-release-service' > SNOMED Release Service </a> page @ github.com ",
				"https://github.com/IHTSDO/snomed-release-service",
				"info@ihtsdotools.org",
				"Apache License, Version 2.0",
				"http://www.apache.org/licenses/LICENSE-2.0"
		);
	}

	@Bean
	public RelativeSwaggerPathProvider setPathProvider(RelativeSwaggerPathProvider pathProvider) {
		this.pathProvider = pathProvider;
		this.pathProvider.setApiResourcePrefix("v1");

		return this.pathProvider;
	}


}
