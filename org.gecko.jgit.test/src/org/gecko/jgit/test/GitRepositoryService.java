/**
 * Copyright (c) 2012 - 2024 Data In Motion and others.
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

import java.io.File;
import java.io.IOException;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.dircache.DirCache;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.util.FileUtils;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;
import org.osgi.service.component.annotations.Deactivate;

/**
 * 
 * @author grune
 * @since Dec 12, 2024
 */
@Component(service = GitRepositoryService.class, configurationPid = "GitRepositoryConfig", configurationPolicy = ConfigurationPolicy.REQUIRE)
public class GitRepositoryService {
	private static final Logger logger = System.getLogger(GitRepositoryService.class.getName());

	private Git git;

	public @interface GitRepositoryConfig {
		String directory();

		String branch() default "main";
	}

	@Activate
	public void activate(GitRepositoryConfig config) throws IOException, IllegalStateException, GitAPIException {
		logger.log(Level.INFO, "Start git repository service");
		File file = new File(config.directory());
		if (file.exists()) {
			FileUtils.delete(file, FileUtils.RECURSIVE);
		}
		git = Git.init().setDirectory(file).setInitialBranch(config.branch()).call();
	}

	public DirCache addFilePattern(String filePattern) throws GitAPIException {
		return git.add().addFilepattern(filePattern).call();
	}

	public RevCommit commit(String name, String email, String message) throws GitAPIException {
		return git.commit().setAuthor(name, email).setMessage(message).call();
	}

	@Deactivate
	public void deactivate() throws IOException {
		git.close();
//		FileUtils.delete(localPath);
	}

}
