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
package org.sonatype.nexus.coelacanth.ldap;

import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import javax.inject.Named;
import javax.inject.Singleton;

/**
 * Holds the currently active {@link LdapConfiguration}.
 *
 * The capability writes here on activation and clears it on passivation; the realm and user manager read from here on
 * every request. The reference is atomic so a configuration change cannot be observed half-applied.
 */
@Named
@Singleton
public class LdapConfigurationHolder
{
  private final AtomicReference<LdapConfiguration> configuration = new AtomicReference<>();

  public Optional<LdapConfiguration> get() {
    return Optional.ofNullable(configuration.get());
  }

  public void set(final LdapConfiguration value) {
    configuration.set(value);
  }

  public void clear() {
    configuration.set(null);
  }

  public boolean isConfigured() {
    return configuration.get() != null;
  }
}
