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
package org.gecko.jgit;

import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.lang.System.Logger;
import java.lang.System.Logger.Level;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

import org.eclipse.jgit.api.FetchCommand;
import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.internal.storage.dfs.DfsRepositoryDescription;
import org.eclipse.jgit.internal.storage.dfs.InMemoryRepository;
import org.eclipse.jgit.lib.ObjectId;
import org.eclipse.jgit.lib.ObjectLoader;
import org.eclipse.jgit.lib.Ref;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.revwalk.RevCommit;
import org.eclipse.jgit.revwalk.RevTree;
import org.eclipse.jgit.revwalk.RevWalk;
import org.eclipse.jgit.storage.file.FileRepositoryBuilder;
import org.eclipse.jgit.transport.RefSpec;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.ssh.jsch.JschConfigSessionFactory;
import org.eclipse.jgit.transport.ssh.jsch.OpenSshConfig;
import org.eclipse.jgit.treewalk.TreeWalk;
import org.eclipse.jgit.treewalk.filter.PathFilter;
import org.eclipse.jgit.util.FS;
import org.osgi.service.component.annotations.Activate;
import org.osgi.service.component.annotations.Component;
import org.osgi.service.component.annotations.ConfigurationPolicy;

import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;

@Component(service = GitService.class, configurationPid = "GitConfig", configurationPolicy = ConfigurationPolicy.REQUIRE, immediate = true)
public class GitService {
	private static final Logger logger = System.getLogger(GitService.class.getName());
	private GitConfig config;
	private Repository repo;
	private Git git;

	@Activate
	public void activate(GitConfig config) throws IOException, GitAPIException {
		logger.log(Level.INFO, "Start git service with repo {0}", config.repo());
		this.config = config;
		SshSessionFactory sshSessionFactory = new JschConfigSessionFactory() {
			@Override
			protected void configure(OpenSshConfig.Host host, Session session) {
			}

			@Override
			protected JSch createDefaultJSch(FS fs) throws JSchException {
				JSch defaultJSch = super.createDefaultJSch(fs);
				defaultJSch.addIdentity(config.privateKey(), config.privateKeyPassphrase());
				return defaultJSch;
			}
		};

		DfsRepositoryDescription repoDesc = new DfsRepositoryDescription();
		if (isRemote(config.repo())) {
			repo = new InMemoryRepository.Builder() //
					.setInitialBranch(config.branch()).setRepositoryDescription(repoDesc) //
					.setFS(FS.detect()).build();
			git = new Git(repo);
			FetchCommand fetchCmd = git.fetch();
			fetchCmd.setRemote(config.repo());
			fetchCmd.setRefSpecs(new RefSpec("+refs/heads/*:refs/heads/*"));
			fetchCmd.setTransportConfigCallback(t -> ((SshTransport) t).setSshSessionFactory(sshSessionFactory));
			fetchCmd.call();
		} else {
			FileRepositoryBuilder builder = new FileRepositoryBuilder();
			File gitDir = new File(config.repo());
			repo = builder.setInitialBranch(config.branch()) // set branch
					.findGitDir(gitDir) // scan up the file system tree
					.build();
			logger.log(Level.INFO, "repo dir {0}", repo.getDirectory());
			git = new Git(repo);
		}
		repo.getObjectDatabase();
	}

	private boolean isRemote(String repo) {
		return repo.startsWith("git") || repo.startsWith("https");
	}

	public void deactivate() {
		repo.close();
	}

	public void loadFile(String file, OutputStream out) throws RevisionSyntaxException, IOException {
		String branch = config.branch();

//		DirCache dirCache = DirCache.read(repo);
		ObjectId lastCommitId = repo.resolve("refs/heads/" + branch);
		try (RevWalk revWalk = new RevWalk(repo); TreeWalk treeWalk = new TreeWalk(repo)) {
			RevCommit commit = revWalk.parseCommit(lastCommitId);
			RevTree tree = commit.getTree();
			treeWalk.addTree(tree);
			treeWalk.setRecursive(true);
//			TreeFilter filter = new TreeFilter() {
//				
//				@Override
//				public boolean shouldBeRecursive() {
//					return true;
//				}
//				
//				@Override
//				public boolean include(TreeWalk walker) throws MissingObjectException, IncorrectObjectTypeException, IOException {
//					return walker.;
//				}
//				
//				@Override
//				public TreeFilter clone() {
//					return null;
//				}
//			};
//			treeWalk.setFilter(filter );
			treeWalk.setFilter(PathFilter.create(file));
			if (!treeWalk.next()) {
				return;
			}
			ObjectId objectId = treeWalk.getObjectId(0);
			ObjectLoader loader = repo.open(objectId);
			loader.copyTo(out);
		}
	}

	public List<String> getBranches() {
		try {
			List<Ref> branches = git.branchList().call();
			return branches.stream().map(Ref::getName).collect(Collectors.toList());
		} catch (GitAPIException e) {
			logger.log(Level.ERROR, () -> "Error getting branch list.", e);
			return Collections.emptyList();
		}
	}

	public Iterable<RevCommit> getLog() throws GitAPIException {
		return git.log().call();

	}
}
