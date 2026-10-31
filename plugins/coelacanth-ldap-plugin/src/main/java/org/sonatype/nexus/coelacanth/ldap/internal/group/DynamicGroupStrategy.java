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
import java.util.List;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.NamingException;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.coelacanth.ldap.GroupStrategy;
import org.sonatype.nexus.coelacanth.ldap.GroupStrategyType;
import org.sonatype.nexus.coelacanth.ldap.LdapConfiguration;
import org.sonatype.nexus.coelacanth.ldap.LdapConnector;
import org.sonatype.nexus.coelacanth.ldap.LdapEntry;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Reads group membership from an attribute on the user entry, typically {@code memberOf}.
 *
 * This is the cheapest directory-backed strategy, a single attribute read, and is the normal choice for Active
 * Directory. It depends on the directory populating the attribute: in OpenLDAP {@code memberOf} comes from an
 * overlay that is not enabled by default, so this strategy returns nothing there until the overlay is configured.
 */
@Named
@Singleton
public class DynamicGroupStrategy
    extends ComponentSupport
    implements GroupStrategy
{
  private final LdapConnector connector;

  @Inject
  public DynamicGroupStrategy(final LdapConnector connector) {
    this.connector = checkNotNull(connector);
  }

  @Override
  public GroupStrategyType getType() {
    return GroupStrategyType.DYNAMIC;
  }

  @Override
  public Set<String> findGroups(final LdapConfiguration config, final LdapEntry user) throws NamingException {
    Set<String> groups = new LinkedHashSet<>();
    if (user == null || user.getDn() == null) {
      return groups;
    }

    String attributeName = config.getUserMemberOfAttribute();
    List<String> values = readMembershipValues(config, user, attributeName);

    for (String value : values) {
      // Values are usually group DNs; reduce them to the group name. Some directories store bare names already.
      String name = extractGroupName(value, config.getGroupIdAttribute());
      if (name != null && !name.isEmpty()) {
        groups.add(name);
      }
    }

    if (groups.isEmpty()) {
      log.debug("Attribute {} on {} yielded no groups. If this is OpenLDAP, confirm the memberOf overlay is enabled.",
          attributeName, user.getDn());
    }
    return groups;
  }

  /**
   * Returns the membership attribute values, reading the entry from the directory when the cached attributes do not
   * already carry them (direct-bind mode never reads the entry).
   */
  private List<String> readMembershipValues(
      final LdapConfiguration config,
      final LdapEntry user,
      final String attributeName) throws NamingException
  {
    Attributes cached = user.getAttributes();
    if (cached != null && cached.get(attributeName) != null) {
      return connector.readAttribute(cached, attributeName);
    }

    DirContext context = null;
    try {
      context = connector.openManagerContext(config);
      Attributes attributes = context.getAttributes(user.getDn(), new String[]{attributeName});
      return connector.readAttribute(attributes, attributeName);
    }
    finally {
      connector.close(context);
    }
  }

  /**
   * Reduces a group DN such as {@code cn=developers,ou=groups,dc=example,dc=com} to {@code developers}.
   */
  private String extractGroupName(final String value, final String groupIdAttribute) {
    if (value == null) {
      return null;
    }

    String trimmed = value.trim();
    int separator = trimmed.indexOf('=');
    if (separator <= 0) {
      // not a DN, treat the whole value as the group name
      return trimmed;
    }

    String attribute = trimmed.substring(0, separator).trim();
    if (!attribute.equalsIgnoreCase(groupIdAttribute)) {
      log.debug("Membership value {} does not start with the configured group id attribute {}", trimmed,
          groupIdAttribute);
    }

    int comma = trimmed.indexOf(',', separator);
    String name = comma < 0 ? trimmed.substring(separator + 1) : trimmed.substring(separator + 1, comma);
    return name.trim();
  }
}
