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
package org.gecko.jgit.api;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;

import org.eclipse.jgit.api.errors.GitAPIException;
import org.eclipse.jgit.errors.RevisionSyntaxException;
import org.eclipse.jgit.revwalk.RevCommit;

public interface GitService {

	TreeResult getFiles();

	TreeResult getFiles(String prefix);
	
	void loadLatestFile(String file, OutputStream out) throws RevisionSyntaxException, IOException;

	List<String> getBranches();

	Iterable<RevCommit> getLog() throws GitAPIException;

	InputStream readLatestFile(String file);

	void loadFile(String commitId, String file, OutputStream out);

	InputStream readFile(String commitId, String file);

	String getBranch();

	String getGitUrl();

	void fetch();

}