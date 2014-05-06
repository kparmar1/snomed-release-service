package org.ihtsdo.buildcloud.service;

import org.hibernate.Hibernate;
import org.ihtsdo.buildcloud.dao.BuildDAO;
import org.ihtsdo.buildcloud.dao.ProductDAO;
import org.ihtsdo.buildcloud.entity.Build;
import org.ihtsdo.buildcloud.entity.Product;
import org.ihtsdo.buildcloud.entity.User;
import org.ihtsdo.buildcloud.service.helper.CompositeKeyHelper;
import org.ihtsdo.buildcloud.service.helper.FilterOption;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

@Service
@Transactional
public class BuildServiceImpl extends EntityServiceImpl<Build> implements BuildService {

	@Autowired
	private BuildDAO buildDAO;

	@Autowired
	private ProductDAO productDAO;

	@Autowired
	public BuildServiceImpl(BuildDAO dao) {
		super(dao);
	}

	@Override
	public List<Build> findAll(EnumSet<FilterOption> filterOptions, User authenticatedUser) {
		return buildDAO.findAll(filterOptions, authenticatedUser);
	}

	@Override
	public Build find(String buildCompositeKey, User authenticatedUser) {
		Long id = CompositeKeyHelper.getId(buildCompositeKey);
		return buildDAO.find(id, authenticatedUser);
	}
	
	@Override
	public List<Build> findForProduct(String releaseCenterBusinessKey, String extensionBusinessKey, String productBusinessKey, User authenticatedUser) {
		List<Build> builds = productDAO.find(releaseCenterBusinessKey, extensionBusinessKey, productBusinessKey, authenticatedUser).getBuilds();
		Hibernate.initialize(builds);
		return builds;
	}
	
	@Override
	public List<Build> findForExtension(String releaseCenterBusinessKey, String extensionBusinessKey, EnumSet<FilterOption> filterOptions, User authenticatedUser) {
		List<Build> builds = buildDAO.findAll(releaseCenterBusinessKey, extensionBusinessKey,  filterOptions, authenticatedUser);
		Hibernate.initialize(builds);
		return builds;
	}

	@Override
	public Build create(String releaseCenterBusinessKey, String extensionBusinessKey, String productBusinessKey, String name, User authenticatedUser) throws Exception{
		Product product = productDAO.find(releaseCenterBusinessKey, extensionBusinessKey, productBusinessKey, authenticatedUser);
		
		if (product == null) {
			throw new Exception ("Unable to find product with path: " + releaseCenterBusinessKey + "/" +  extensionBusinessKey + "/" + productBusinessKey);
		}
		
		Build build = new Build(name);
		product.addBuild(build);
		buildDAO.save(build);
		return build;
	}	

}
