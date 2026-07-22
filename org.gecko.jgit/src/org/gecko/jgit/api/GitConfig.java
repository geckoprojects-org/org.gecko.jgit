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

import org.osgi.service.metatype.annotations.ObjectClassDefinition;

@ObjectClassDefinition
public @interface GitConfig {
	String repo();

	String branch() default "main";

	String privateKey();

	String privateKeyPassphrase();

	/**
	 * Path to an OpenSSH {@code known_hosts} file used to verify the SSH server's
	 * host key. When empty, the Apache MINA sshd backend falls back to its default
	 * location ({@code ~/.ssh/known_hosts}). Only relevant for SSH remotes.
	 */
	String knownHosts() default "";
}