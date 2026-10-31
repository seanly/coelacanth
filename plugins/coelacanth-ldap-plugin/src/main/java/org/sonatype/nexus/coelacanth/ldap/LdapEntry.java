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

import javax.annotation.Nullable;
import javax.naming.directory.Attributes;

/**
 * A directory entry located for a login name.
 *
 * {@link #getAttributes()} is {@code null} in direct-bind mode, where the DN is derived from the configured pattern
 * without reading the entry.
 */
public class LdapEntry
{
  private final String dn;

  private final String username;

  private final Attributes attributes;

  public LdapEntry(final String dn, final String username, @Nullable final Attributes attributes) {
    this.dn = dn;
    this.username = username;
    this.attributes = attributes;
  }

  public String getDn() {
    return dn;
  }

  public String getUsername() {
    return username;
  }

  @Nullable
  public Attributes getAttributes() {
    return attributes;
  }

  @Override
  public String toString() {
    return "LdapEntry{dn=" + dn + ", username=" + username + '}';
  }
}
