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
package org.sonatype.nexus.coelacanth.ldap.internal.group;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.coelacanth.ldap.GroupStrategy;
import org.sonatype.nexus.coelacanth.ldap.GroupStrategyType;
import org.sonatype.nexus.coelacanth.ldap.LdapConfiguration;
import org.sonatype.nexus.coelacanth.ldap.LdapEntry;

/**
 * Derives group membership from the user's position in the DN tree.
 *
 * {@code uid=jane,ou=dev,ou=uk,dc=example,dc=com} yields {@code dev} and {@code uk}. No directory round trip is
 * needed, which makes this the cheapest strategy, but it couples group names to the DN tree: moving a user between
 * organizational units changes their groups.
 */
@Named
@Singleton
public class HierarchyGroupStrategy
    extends ComponentSupport
    implements GroupStrategy
{
  @Override
  public GroupStrategyType getType() {
    return GroupStrategyType.HIERARCHY;
  }

  @Override
  public Set<String> findGroups(final LdapConfiguration config, final LdapEntry user) {
    Set<String> groups = new LinkedHashSet<>();
    if (user == null || user.getDn() == null) {
      return groups;
    }

    // Skip the leading RDN, which identifies the user rather than a group
    String[] components = user.getDn().split(",");
    for (int i = 1; i < components.length; i++) {
      String component = components[i].trim();
      int separator = component.indexOf('=');
      if (separator <= 0 || separator == component.length() - 1) {
        continue;
      }

      String attribute = component.substring(0, separator).trim();
      String value = component.substring(separator + 1).trim();

      // Only organizational units name groups; dc components name the directory root
      if ("ou".equalsIgnoreCase(attribute) && !value.isEmpty()) {
        groups.add(value);
      }
    }

    log.debug("Hierarchy strategy resolved groups {} for {}", groups, user.getDn());
    return groups;
  }
}
