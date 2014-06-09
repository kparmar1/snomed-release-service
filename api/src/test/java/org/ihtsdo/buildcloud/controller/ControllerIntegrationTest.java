package org.ihtsdo.buildcloud.controller;

import org.ihtsdo.buildcloud.security.SecurityFilter;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockFilterConfig;
import org.springframework.mock.web.MockServletContext;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.AbstractJUnit4SpringContextTests;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.test.context.web.WebAppConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import javax.servlet.ServletException;
import java.nio.charset.Charset;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/applicationContext.xml"})
@WebAppConfiguration
@Transactional
public abstract class ControllerIntegrationTest extends AbstractJUnit4SpringContextTests {

	public static final MediaType APPLICATION_JSON_UTF8 = new MediaType(MediaType.APPLICATION_JSON.getType(),
			MediaType.APPLICATION_JSON.getSubtype(),
			Charset.forName("utf8")
		);

	public static final String ROOT_URL = "http://localhost:80";

	protected MockMvc mockMvc;

	@Autowired
	private WebApplicationContext wac;

	@Before
	public void setup() throws ServletException {
		// Create SecurityFilter
		SecurityFilter securityFilter = new SecurityFilter();
		MockServletContext mockServletContext = new MockServletContext();
		mockServletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, wac);
		securityFilter.init(new MockFilterConfig(mockServletContext));

		mockMvc = MockMvcBuilders.webAppContextSetup(wac)
				.addFilter(securityFilter, "/*") // Add SecurityFilter
				.build();
	}

}
