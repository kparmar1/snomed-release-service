package org.ihtsdo.buildcloud.service;

import org.ihtsdo.buildcloud.entity.ReleaseCenter;
import org.ihtsdo.buildcloud.entity.User;

import java.util.List;

public interface ReleaseCenterService extends EntityService<ReleaseCenter> {

	List<ReleaseCenter> findAll(User authenticatedUser);

	ReleaseCenter find(String businessKey, User authenticatedUser);

	ReleaseCenter create(String name, String shortName, User authenticatedUser);

	void update(ReleaseCenter center);

}