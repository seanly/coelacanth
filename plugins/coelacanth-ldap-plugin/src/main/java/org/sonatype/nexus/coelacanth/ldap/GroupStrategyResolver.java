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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.NamingException;

import org.sonatype.goodies.common.ComponentSupport;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Dispatches group resolution to the {@link GroupStrategy} selected in the configuration.
 */
@Named
@Singleton
public class GroupStrategyResolver
    extends ComponentSupport
{
  private final Map<GroupStrategyType, GroupStrategy> strategies;

  @Inject
  public GroupStrategyResolver(final List<GroupStrategy> strategies) {
    checkNotNull(strategies);
    this.strategies = strategies.stream()
        .collect(Collectors.toMap(GroupStrategy::getType, Function.identity()));
  }

  /**
   * Resolves the user's groups, returning an empty set when group mapping is disabled or the directory lookup fails.
   *
   * A failure here must not deny access: the user has already authenticated, and the only consequence of an empty
   * result is that externally mapped roles are missing. Throwing would turn a directory hiccup into a hard login
   * failure.
   */
  public Set<String> findGroups(final LdapConfiguration config, final LdapEntry user) {
    GroupStrategyType type = config.getGroupStrategy();
    if (type == null || type == GroupStrategyType.NONE) {
      return Collections.emptySet();
    }

    GroupStrategy strategy = strategies.get(type);
    if (strategy == null) {
      log.warn("No group strategy registered for {}", type);
      return Collections.emptySet();
    }

    try {
      return strategy.findGroups(config, user);
    }
    catch (NamingException e) {
      log.warn("Group lookup failed for {} using {} strategy; proceeding without external roles", user.getDn(), type,
          e);
      return Collections.emptySet();
    }
  }
}
