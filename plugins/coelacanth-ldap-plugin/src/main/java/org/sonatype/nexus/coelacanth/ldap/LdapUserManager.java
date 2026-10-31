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

import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.NamingException;

import org.sonatype.nexus.security.role.RoleIdentifier;
import org.sonatype.nexus.security.user.AbstractReadOnlyUserManager;
import org.sonatype.nexus.security.user.User;
import org.sonatype.nexus.security.user.UserNotFoundException;
import org.sonatype.nexus.security.user.UserSearchCriteria;
import org.sonatype.nexus.security.user.UserStatus;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Exposes directory users to Nexus so they can be searched and granted roles in the user administration screen.
 *
 * Read-only: the directory is the system of record, so every write method inherited from
 * {@link AbstractReadOnlyUserManager} throws. The source name {@code LDAP} matches the mapping that core's
 * {@code RealmToSource} already defines for {@code LdapRealm}.
 */
@Named(LdapUserManager.SOURCE)
@Singleton
public class LdapUserManager
    extends AbstractReadOnlyUserManager
{
  public static final String SOURCE = "LDAP";

  private final LdapConfigurationHolder configurationHolder;

  private final LdapConnector connector;

  private final GroupStrategyResolver groupStrategyResolver;

  @Inject
  public LdapUserManager(
      final LdapConfigurationHolder configurationHolder,
      final LdapConnector connector,
      final GroupStrategyResolver groupStrategyResolver)
  {
    this.configurationHolder = checkNotNull(configurationHolder);
    this.connector = checkNotNull(connector);
    this.groupStrategyResolver = checkNotNull(groupStrategyResolver);
  }

  @Override
  public String getSource() {
    return SOURCE;
  }

  @Override
  public String getAuthenticationRealmName() {
    return LdapRealm.NAME;
  }

  @Override
  public boolean isConfigured() {
    return configurationHolder.isConfigured();
  }

  @Override
  public User getUser(final String userId) throws UserNotFoundException {
    LdapConfiguration config = requireConfiguration(userId);

    try {
      LdapEntry entry = connector.findUser(config, userId).orElseThrow(() -> new UserNotFoundException(userId));
      return toUser(config, entry, true);
    }
    catch (NamingException e) {
      log.warn("Directory lookup failed for {}", userId, e);
      throw new UserNotFoundException(userId, "Directory unavailable", e);
    }
  }

  @Override
  public User getUser(final String userId, final Set<String> roleIds) throws UserNotFoundException {
    LdapConfiguration config = requireConfiguration(userId);

    try {
      LdapEntry entry = connector.findUser(config, userId).orElseThrow(() -> new UserNotFoundException(userId));
      // roleIds are already known, so skip the group lookup entirely
      User user = toUser(config, entry, false);
      if (roleIds != null) {
        roleIds.forEach(roleId -> user.addRole(new RoleIdentifier(SOURCE, roleId)));
      }
      return user;
    }
    catch (NamingException e) {
      log.warn("Directory lookup failed for {}", userId, e);
      throw new UserNotFoundException(userId, "Directory unavailable", e);
    }
  }

  @Override
  public Set<String> listUserIds() {
    Set<String> ids = new LinkedHashSet<>();
    for (User user : listUsers()) {
      ids.add(user.getUserId());
    }
    return ids;
  }

  @Override
  public Set<User> listUsers() {
    return searchUsers(new UserSearchCriteria());
  }

  @Override
  public Set<User> searchUsers(final UserSearchCriteria criteria) {
    LdapConfiguration config = configurationHolder.get().orElse(null);
    if (config == null) {
      return Collections.emptySet();
    }

    if (criteria != null && criteria.getSource() != null && !SOURCE.equals(criteria.getSource())) {
      return Collections.emptySet();
    }

    String prefix = criteria == null ? null : criteria.getUserId();
    int limit = resolveLimit(config, criteria);

    try {
      List<LdapEntry> entries = connector.listUsers(config, prefix, limit);
      Set<User> users = new LinkedHashSet<>(entries.size());
      for (LdapEntry entry : entries) {
        // Group lookup is skipped here: doing it per row would issue one directory search per user and make the
        // administration screen unusable on a directory of any size.
        users.add(toUser(config, entry, false));
      }

      // Role filtering still has to be honoured, so resolve groups only for the narrowed-down candidates
      if (criteria != null && criteria.getOneOfRoleIds() != null && !criteria.getOneOfRoleIds().isEmpty()) {
        return filterByRoles(config, entries, criteria);
      }

      return users;
    }
    catch (NamingException e) {
      log.warn("Directory user search failed", e);
      return Collections.emptySet();
    }
  }

  private Set<User> filterByRoles(
      final LdapConfiguration config,
      final List<LdapEntry> entries,
      final UserSearchCriteria criteria)
  {
    Set<User> matched = new LinkedHashSet<>();
    for (LdapEntry entry : entries) {
      User user = toUser(config, entry, true);
      Set<String> roleIds = new HashSet<>();
      user.getRoles().forEach(role -> roleIds.add(role.getRoleId()));
      roleIds.retainAll(criteria.getOneOfRoleIds());
      if (!roleIds.isEmpty()) {
        matched.add(user);
      }
    }
    return matched;
  }

  /**
   * Builds the Nexus user from a directory entry.
   *
   * @param resolveGroups whether to perform the group lookup, which costs a directory round trip
   */
  private User toUser(final LdapConfiguration config, final LdapEntry entry, final boolean resolveGroups) {
    User user = new User();
    user.setUserId(entry.getUsername());
    user.setSource(SOURCE);
    user.setReadOnly(true);
    // The directory owns the lifecycle; a user it returns is usable. Locked or expired accounts fail at bind time.
    user.setStatus(UserStatus.active);

    if (entry.getAttributes() != null) {
      connector.readFirst(entry.getAttributes(), config.getEmailAttribute()).ifPresent(user::setEmailAddress);
      Optional<String> realName = connector.readFirst(entry.getAttributes(), config.getRealNameAttribute());
      if (realName.isPresent()) {
        user.setName(realName.get());
      }
      else {
        user.setName(entry.getUsername());
      }
    }
    else {
      user.setName(entry.getUsername());
    }

    if (resolveGroups) {
      for (String group : groupStrategyResolver.findGroups(config, entry)) {
        user.addRole(new RoleIdentifier(SOURCE, group));
      }
    }

    return user;
  }

  private int resolveLimit(final LdapConfiguration config, final UserSearchCriteria criteria) {
    if (criteria != null && criteria.getLimit() != null && criteria.getLimit() > 0) {
      return Math.min(criteria.getLimit(), config.getMaxSearchResults());
    }
    return config.getMaxSearchResults();
  }

  private LdapConfiguration requireConfiguration(final String userId) throws UserNotFoundException {
    return configurationHolder.get()
        .orElseThrow(() -> new UserNotFoundException(userId, "LDAP realm is not configured", null));
  }
}
