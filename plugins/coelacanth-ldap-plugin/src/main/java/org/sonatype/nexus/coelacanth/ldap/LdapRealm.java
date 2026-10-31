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
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.NamingException;


import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.authz.SimpleAuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.eclipse.sisu.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Authenticates against an LDAP directory and maps directory groups onto Nexus roles.
 *
 * The realm name is {@code LdapRealm} so that the user source mapping already present in
 * {@code org.sonatype.nexus.security.internal.RealmToSource} resolves users of this realm to the {@code LDAP}
 * source without any change to core.
 *
 * Registering as {@code @Named} + {@code @Singleton} + {@code @Description} is all that is required for the realm to
 * appear in the realm administration screen; {@code RealmManagerImpl} discovers realms through the bean locator.
 */
@Named(LdapRealm.NAME)
@Singleton
@Description("LDAP Realm")
public class LdapRealm
    extends AuthorizingRealm
{
  // AuthorizingRealm keeps its own logger private, so declare one here
  private static final Logger log = LoggerFactory.getLogger(LdapRealm.class);

  public static final String NAME = "LdapRealm";

  private final LdapConfigurationHolder configurationHolder;

  private final LdapConnector connector;

  private final GroupStrategyResolver groupStrategyResolver;

  @Inject
  public LdapRealm(
      final LdapConfigurationHolder configurationHolder,
      final LdapConnector connector,
      final GroupStrategyResolver groupStrategyResolver)
  {
    this.configurationHolder = checkNotNull(configurationHolder);
    this.connector = checkNotNull(connector);
    this.groupStrategyResolver = checkNotNull(groupStrategyResolver);

    setName(NAME);

    // Credentials are verified by binding to the directory inside doGetAuthenticationInfo, so Shiro must not try to
    // compare them again against a stored hash it does not have.
    setCredentialsMatcher(new AllowAllCredentialsMatcher());

    // Authentication is NOT cached: a cache hit would skip the bind and keep accepting a password that the directory
    // has since changed or locked out. Authorization is cached, because the group lookup is the expensive part and
    // every artifact request triggers one.
    setAuthenticationCachingEnabled(false);
    setAuthorizationCachingEnabled(true);
    setAuthorizationCacheName(NAME + ".authorization");
  }

  @Override
  public boolean supports(final AuthenticationToken token) {
    return token instanceof UsernamePasswordToken && configurationHolder.isConfigured();
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token)
      throws AuthenticationException
  {
    if (!(token instanceof UsernamePasswordToken)) {
      return null;
    }

    LdapConfiguration config = configurationHolder.get().orElse(null);
    if (config == null) {
      log.debug("LDAP realm is enabled but not configured");
      return null;
    }

    UsernamePasswordToken upToken = (UsernamePasswordToken) token;
    String username = upToken.getUsername();
    if (username == null || username.isBlank()) {
      return null;
    }

    String password = upToken.getPassword() == null ? null : new String(upToken.getPassword());

    try {
      Optional<LdapEntry> entry = connector.findUser(config, username);
      if (entry.isEmpty()) {
        log.debug("No directory entry for {}", username);
        return null;
      }

      if (!connector.authenticate(config, entry.get().getDn(), password)) {
        // Returning null lets the next realm in the chain try; throwing would short-circuit the whole chain
        log.debug("Directory rejected credentials for {}", username);
        return null;
      }

      log.debug("Authenticated {} against directory as {}", username, entry.get().getDn());
      return new SimpleAuthenticationInfo(username, upToken.getPassword(), getName());
    }
    catch (NamingException e) {
      // A directory that cannot be reached is an infrastructure fault, not a wrong password. Surfacing it as an
      // exception keeps it out of the "bad credentials" audit path and lets the health check report it.
      throw new AuthenticationException("LDAP directory unavailable while authenticating " + username, e);
    }
  }
  @Override
  protected AuthorizationInfo doGetAuthorizationInfo(final PrincipalCollection principals) {
    if (principals == null || principals.isEmpty()) {
      return null;
    }

    LdapConfiguration config = configurationHolder.get().orElse(null);
    if (config == null) {
      return null;
    }

    String username = principals.getPrimaryPrincipal().toString();

    try {
      Optional<LdapEntry> entry = connector.findUser(config, username);
      if (entry.isEmpty()) {
        return null;
      }

      Set<String> groups = groupStrategyResolver.findGroups(config, entry.get());
      if (groups.isEmpty()) {
        return null;
      }

      // Group names become role ids. Nexus resolves them against roles whose source is LDAP, so an unmapped group
      // simply contributes nothing rather than failing.
      SimpleAuthorizationInfo info = new SimpleAuthorizationInfo();
      info.addRoles(groups);
      log.debug("Granting roles {} to {}", groups, username);
      return info;
    }
    catch (NamingException e) {
      log.warn("Unable to resolve directory groups for {}", username, e);
      return null;
    }
  }

  /**
   * Drops cached authorization data so a configuration change or a group change takes effect without a restart.
   */
  public void clearCaches() {
    if (getAuthorizationCache() != null) {
      getAuthorizationCache().clear();
    }
  }
}
