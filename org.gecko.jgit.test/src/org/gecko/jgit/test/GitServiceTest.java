/**
 * Copyright (c) 2012 - 2023 Data In Motion and others.
 * All rights reserved. 
 * 
 * This program and the accompanying materials are made
 * available under the terms of the Eclipse Public License 2.0
 * which is available at https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 * 
 * Contributors:
 *     Data In Motion - initial API and implementation
 */
package org.gecko.jgit.test;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.lib.PersonIdent;
import org.eclipse.jgit.revwalk.RevCommit;
import org.gecko.jgit.GitService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.service.cm.annotations.RequireConfigurationAdmin;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.annotation.Property;
import org.osgi.test.common.annotation.config.WithFactoryConfiguration;
import org.osgi.test.common.service.ServiceAware;
import org.osgi.test.junit5.cm.ConfigurationExtension;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;

@RequireConfigurationAdmin
@ExtendWith(ServiceExtension.class)
@ExtendWith(ConfigurationExtension.class)
@ExtendWith(BundleContextExtension.class)
@WithFactoryConfiguration(factoryPid = "GitRepositoryConfig", location = "?", name = "repo", properties = {
		@Property(key = "directory", value = "testRepo"), //
		@Property(key = "branch", value = "main") })
public class GitServiceTest {

	/** FOO_BAR */
	private static final String FILE_CONTENT = "fooBar";

	@BeforeEach
	public void before(@InjectService(cardinality = 0) ServiceAware<GitRepositoryService> repoAware)
			throws InterruptedException, GitAPIException, IOException {
		GitRepositoryService repo = repoAware.waitForService(5000);
		assertThat(repo).isNotNull();
		Files.writeString(Paths.get("testRepo/test"), FILE_CONTENT);
		repo.addFilePattern("test");
		repo.commit("Hans Wurst", "hw@example.com", "add test");
	}

	@Test
	@WithFactoryConfiguration(factoryPid = "GitConfig", location = "?", name = "git", properties = {
			@Property(key = "repo", value = "testRepo"), //
			@Property(key = "branch", value = "main") })
	public void testLog(@InjectService(cardinality = 0) ServiceAware<GitService> gsAware)
			throws InterruptedException, GitAPIException {
		GitService service = gsAware.waitForService(5000l);
		List<String> branches = service.getBranches();
		assertThat(branches).hasSize(1);

		Iterable<RevCommit> logs = service.getLog();
		for (RevCommit commit : logs) {
			assertThat(commit).extracting(c -> c.getAuthorIdent())
					.extracting(PersonIdent::getName, PersonIdent::getEmailAddress)
					.containsExactly("Hans Wurst", "hw@example.com");
		}
	}

	@Test
	@WithFactoryConfiguration(factoryPid = "GitConfig", location = "?", name = "git", properties = {
			@Property(key = "repo", value = "testRepo"), //
			@Property(key = "branch", value = "main") })
	public void testLoad(@InjectService(cardinality = 0) ServiceAware<GitService> gsAware)
			throws InterruptedException, IOException {
		GitService service = gsAware.waitForService(5000l);
		List<String> branches = service.getBranches();
		assertThat(branches).hasSize(1);

		ByteArrayOutputStream o = new ByteArrayOutputStream();
		service.loadFile("test", o);
		String content = new String(o.toByteArray());
		assertThat(content).isEqualTo(FILE_CONTENT);
	}

}
