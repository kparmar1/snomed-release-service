package org.ihtsdo.buildcloud.controller;

import org.ihtsdo.buildcloud.dao.s3.S3Client;
import org.ihtsdo.buildcloud.dao.s3.TestS3Client;
import org.ihtsdo.buildcloud.service.execution.transform.PesudoUUIDGenerator;
import org.ihtsdo.buildcloud.service.execution.transform.UUIDGenerator;
import org.junit.After;
import org.junit.Assert;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletException;

import java.nio.charset.Charset;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/testDispatcherServletContext.xml"})
@WebAppConfiguration
public abstract class AbstractControllerTest {

	public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(),
			MediaType.APPLICATION_JSON.getSubtype(),
			Charset.forName("utf8")
		);

	public static final String ROOT_URL = "http://localhost:80";

	protected MockMvc mockMvc;

	@Autowired
	private WebApplicationContext wac;

	@Autowired
	private S3Client s3Client;

	@Autowired
	private UUIDGenerator uuidGenerator;

	@Autowired
	private String executionBucketName;

	@Autowired
	private String publishedBucketName;

	@Before
	public void setup() throws ServletException, Exception {
		mockMvc = MockMvcBuilders.webAppContextSetup(wac).build();
		Assert.assertNotNull(mockMvc);
		((PesudoUUIDGenerator)uuidGenerator).reset();

		TestS3Client testS3Client = (TestS3Client) s3Client;
		testS3Client.freshBucketStore();
		testS3Client.createBucket(executionBucketName);
		testS3Client.createBucket(publishedBucketName);
	}

	@After
	public void tearDown() {
		((PesudoUUIDGenerator)uuidGenerator).reset();
	}

}
