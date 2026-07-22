/**
 * Copyright (c) 2012 - 2026 Data In Motion and others.
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

import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Dictionary;
import java.util.Hashtable;

import org.eclipse.jgit.api.Git;
import org.eclipse.jgit.lib.Repository;
import org.eclipse.jgit.transport.Daemon;
import org.eclipse.jgit.transport.DaemonService;
import org.eclipse.jgit.util.FileUtils;
import org.gecko.jgit.api.GitService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.osgi.service.cm.Configuration;
import org.osgi.service.cm.ConfigurationAdmin;
import org.osgi.service.cm.annotations.RequireConfigurationAdmin;
import org.osgi.test.common.annotation.InjectService;
import org.osgi.test.common.service.ServiceAware;
import org.osgi.test.junit5.cm.ConfigurationExtension;
import org.osgi.test.junit5.context.BundleContextExtension;
import org.osgi.test.junit5.service.ServiceExtension;

/**
 * Regression test for the SSH-only transport fix: a {@link GitService} configured
 * against an anonymous {@code git://} remote must activate and read.
 *
 * <p>Before the fix, {@code GitServiceImpl.activate()} installed a
 * {@code TransportConfigCallback} that <em>unconditionally</em> cast the JGit
 * {@code Transport} to {@code SshTransport}. Over {@code git://} the transport is a
 * {@code TransportGitAnon}, so activation threw a {@code ClassCastException}, the DS
 * component never registered, and {@code waitForService} below would return
 * {@code null}. The fix only attaches the SSH session factory for actual
 * {@code SshTransport}s, so non-SSH remotes fetch with JGit's default transport.
 *
 * <p>The remote is served in-process by JGit's own {@link Daemon} over the anonymous
 * git protocol on an ephemeral port — no external git binary, no container, no TLS or
 * credentials. The config is pushed via {@link ConfigurationAdmin} rather than the
 * {@code @WithFactoryConfiguration} annotation because the port is only known at runtime.
 */
@RequireConfigurationAdmin
@ExtendWith(ServiceExtension.class)
@ExtendWith(ConfigurationExtension.class)
@ExtendWith(BundleContextExtension.class)
public class GitAnonymousTransportTest {

	private static final String FILE_CONTENT = "fooBar";

	private Path repoDir;
	private Git served;
	private Daemon daemon;
	private Configuration configuration;

	@AfterEach
	public void cleanup() throws Exception {
		if (configuration != null) {
			configuration.delete();
		}
		if (daemon != null) {
			daemon.stop();
		}
		if (served != null) {
			served.close();
		}
		if (repoDir != null && Files.exists(repoDir)) {
			FileUtils.delete(repoDir.toFile(), FileUtils.RECURSIVE | FileUtils.SKIP_MISSING);
		}
	}

	@Test
	public void testFetchOverAnonymousGitProtocol(
			@InjectService(cardinality = 0) ServiceAware<GitService> gsAware,
			@InjectService ConfigurationAdmin configAdmin) throws Exception {

		// A real repo on disk with a single commit on 'main'.
		repoDir = Files.createTempDirectory("gecko-jgit-anon");
		served = Git.init().setDirectory(repoDir.toFile()).setInitialBranch("main").call();
		Files.writeString(repoDir.resolve("test"), FILE_CONTENT);
		served.add().addFilepattern("test").call();
		served.commit().setAuthor("Hans Wurst", "hw@example.com").setMessage("add test").call();

		// Serve it over git:// on an ephemeral port. Daemon.start() rebinds the socket and
		// updates getAddress(), so the actual port is known only after start().
		Repository servedRepo = served.getRepository();
		daemon = new Daemon(new InetSocketAddress("127.0.0.1", 0));
		DaemonService uploadPack = daemon.getService("upload-pack");
		uploadPack.setEnabled(true);
		uploadPack.setOverridable(false); // force upload-pack on regardless of per-repo config
		daemon.setRepositoryResolver((req, name) -> servedRepo);
		daemon.start();
		String url = "git://127.0.0.1:" + daemon.getAddress().getPort() + "/served";

		// Configure the real GitServiceImpl against the git:// URL (no privateKey).
		configuration = configAdmin.createFactoryConfiguration("GitConfig", "?");
		Dictionary<String, Object> props = new Hashtable<>();
		props.put("repo", url);
		props.put("branch", "main");
		configuration.update(props);

		// Would be null if activate() still threw ClassCastException on the non-SSH transport.
		GitService service = gsAware.waitForService(10000l);
		assertThat(service).as("GitService activated over git://").isNotNull();
		assertThat(service.getGitUrl()).isEqualTo(url);
		assertThat(service.getBranches()).contains("refs/heads/main");
		assertThat(service.getFiles().getFiles()).contains("test");
	}

}
