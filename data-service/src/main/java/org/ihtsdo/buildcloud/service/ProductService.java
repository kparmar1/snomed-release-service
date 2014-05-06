package org.ihtsdo.buildcloud.service;

import org.ihtsdo.buildcloud.entity.Product;
import org.ihtsdo.buildcloud.entity.User;

import java.util.List;

public interface ProductService extends EntityService<Product> {

	List<Product> findAll(String releaseCenterBusinessKey, String extensionBusinessKey, User authenticatedUser) throws Exception;

	Product find(String releaseCenterBusinessKey, String extensionBusinessKey, String productBusinessKey, User authenticatedUser);

	Product create(String releaseCenterBusinessKey, String extensionBusinessKey, String name, User authenticatedUser);

}
