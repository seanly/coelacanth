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

import java.util.Set;

import javax.naming.NamingException;


/**
 * Resolves the group names a user belongs to.
 *
 * One implementation per {@link GroupStrategyType}, because directories expose membership in ways that share no
 * common attribute: the group may list its members, the user may list its groups, or membership may be implied by
 * the DN tree alone.
 */
public interface GroupStrategy
{
  /**
   * The strategy this implementation handles.
   */
  GroupStrategyType getType();

  /**
   * Returns the group names for the given user entry, never {@code null}.
   *
   * @throws NamingException if the directory cannot be reached
   */
  Set<String> findGroups(LdapConfiguration config, LdapEntry user) throws NamingException;
}
