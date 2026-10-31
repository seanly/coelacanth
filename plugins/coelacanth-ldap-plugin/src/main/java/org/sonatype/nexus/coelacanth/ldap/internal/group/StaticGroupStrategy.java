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
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.coelacanth.ldap.GroupStrategy;
import org.sonatype.nexus.coelacanth.ldap.GroupStrategyType;
import org.sonatype.nexus.coelacanth.ldap.LdapConfiguration;
import org.sonatype.nexus.coelacanth.ldap.LdapConnector;
import org.sonatype.nexus.coelacanth.ldap.LdapEntry;
import org.sonatype.nexus.coelacanth.ldap.LdapFilters;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Finds the groups whose member attribute references the user.
 *
 * Used where the directory records membership on the group entry ({@code groupOfNames} with {@code member}, or
 * {@code groupOfUniqueNames} with {@code uniqueMember}) and does not populate a reverse attribute on the user. This
 * costs one subtree search per authorization lookup, so the group search base should be as narrow as possible.
 */
@Named
@Singleton
public class StaticGroupStrategy
    extends ComponentSupport
    implements GroupStrategy
{
  private final LdapConnector connector;

  @Inject
  public StaticGroupStrategy(final LdapConnector connector) {
    this.connector = checkNotNull(connector);
  }

  @Override
  public GroupStrategyType getType() {
    return GroupStrategyType.STATIC;
  }

  @Override
  public Set<String> findGroups(final LdapConfiguration config, final LdapEntry user) throws NamingException {
    Set<String> groups = new LinkedHashSet<>();
    if (user == null || user.getDn() == null) {
      return groups;
    }

    String memberValue = memberValueFor(config, user);
    String filter = "(&(objectClass=" + LdapFilters.escapeFilterValue(config.getGroupObjectClass()) + ")("
        + config.getGroupMemberAttribute() + "=" + LdapFilters.escapeFilterValue(memberValue) + "))";
    String base = groupSearchBase(config);

    SearchControls controls = new SearchControls();
    controls.setSearchScope(SearchControls.SUBTREE_SCOPE);
    controls.setReturningAttributes(new String[]{config.getGroupIdAttribute()});
    controls.setCountLimit(config.getMaxSearchResults());
    controls.setTimeLimit(config.getReadTimeoutMillis());

    DirContext context = null;
    NamingEnumeration<SearchResult> results = null;
    try {
      context = connector.openManagerContext(config);
      results = context.search(base, filter, controls);
      while (results.hasMore()) {
        SearchResult result = results.next();
        Optional<String> name = connector.readFirst(result.getAttributes(), config.getGroupIdAttribute());
        name.filter(value -> !value.isBlank()).ifPresent(groups::add);
      }
    }
    finally {
      connector.closeEnumeration(results);
      connector.close(context);
    }

    log.debug("Static strategy resolved groups {} for {}", groups, user.getDn());
    return groups;
  }

  /**
   * Builds the value the group's member attribute is expected to hold for this user.
   *
   * Most directories store the full user DN; some store the bare login name, which the {@code ${username}} format
   * selects.
   */
  private String memberValueFor(final LdapConfiguration config, final LdapEntry user) {
    String format = config.getGroupMemberFormat();
    if (format != null && format.contains("${username}")) {
      return format.replace("${username}", user.getUsername());
    }
    if (format != null && format.contains("${dn}")) {
      return format.replace("${dn}", user.getDn());
    }
    return user.getDn();
  }

  /**
   * Falls back to the user search base when no dedicated group base is configured.
   */
  private String groupSearchBase(final LdapConfiguration config) {
    String base = config.getGroupSearchBase();
    if (base != null && !base.isBlank()) {
      return base;
    }
    return connector.searchBaseOf(config);
  }
}
