package org.ihtsdo.buildcloud.dao;

import java.util.List;

import org.ihtsdo.buildcloud.entity.ReleaseCenter;
import org.ihtsdo.buildcloud.test.TestUtils;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.transaction.annotation.Transactional;

@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(locations={"/test/testDataServiceContext.xml"})
@Transactional
public class ReleaseCenterDAOImplTest {

	@Autowired
	private ReleaseCenterDAO dao;

	@Test
	public void testFindAll() {
		Assert.assertNotNull(dao);
		List<ReleaseCenter> centers = dao.findAll(TestUtils.TEST_USER);
		Assert.assertEquals(4, centers.size());
		ReleaseCenter internationalReleaseCenter = centers.get(0);
		Assert.assertEquals("International Release Center", internationalReleaseCenter.getName());
		Assert.assertEquals("International", internationalReleaseCenter.getShortName());
		Assert.assertEquals("international", internationalReleaseCenter.getBusinessKey());
	}

	@Test
	public void testFind() {
		ReleaseCenter releaseCenter = dao.find("international", TestUtils.TEST_USER);
		Assert.assertNotNull(releaseCenter);
		Assert.assertEquals("International Release Center", releaseCenter.getName());
		Assert.assertEquals("International", releaseCenter.getShortName());
	}

}
