/*
 * Sonatype Nexus (TM) Open Source Version
 * Copyright (c) 2008-present Sonatype, Inc.
 * All rights reserved. Includes the third-party code listed at http://links.sonatype.com/products/nexus/oss/attributions.
 *
 * This program and the accompanying materials are made available under the terms of the Eclipse Public License Version 1.0,
 * which accompanies this distribution and is available at http://www.eclipse.org/legal/epl-v10.html.
 *
 * Sonatype Nexus (TM) Professional Version is available from Sonatype, Inc. "Sonatype" and "Sonatype Nexus" are trademarks
 * of Sonatype, Inc. Apache Maven is a trademark of the Apache Software Foundation. M2eclipse is a trademark of the
 * Eclipse Foundation. All other trademarks are the property of their respective owners.
 */
package org.sonatype.nexus.coelacanth.usertoken.internal;

import javax.inject.Named;

import org.sonatype.nexus.security.FilterChainModule;

import com.google.inject.AbstractModule;

/**
 * Adds {@link UserTokenContentFilter} to the repository filter chain.
 *
 * Chains contributed for the same path pattern are appended rather than replaced, so this adds the filter to the
 * chain the repository HTTP bridge already declares without having to modify that plugin. The filter is written to
 * be independent of its position in the chain, because the order in which contributions from different bundles are
 * applied is not guaranteed.
 */
@Named
public class UserTokenFilterModule
    extends AbstractModule
{
  /**
   * Must match {@code HttpBridgeModule.MOUNT_POINT}.
   */
  private static final String REPOSITORY_MOUNT_POINT = "/repository";

  @Override
  protected void configure() {
    install(new FilterChainModule()
    {
      @Override
      protected void configure() {
        addFilterChain(REPOSITORY_MOUNT_POINT + "/**", UserTokenContentFilter.NAME);
      }
    });
  }
}
