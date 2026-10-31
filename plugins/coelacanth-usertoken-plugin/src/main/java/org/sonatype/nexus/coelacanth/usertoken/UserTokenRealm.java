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
package org.sonatype.nexus.coelacanth.usertoken;

import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Provider;
import javax.inject.Singleton;
import javax.servlet.ServletRequest;

import org.sonatype.nexus.security.UserPrincipalsHelper;
import org.sonatype.nexus.security.user.UserNotFoundException;

import org.apache.shiro.authc.AuthenticationException;
import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.AuthenticationToken;
import org.apache.shiro.authc.SimpleAuthenticationInfo;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.authc.credential.AllowAllCredentialsMatcher;
import org.apache.shiro.authz.AuthorizationInfo;
import org.apache.shiro.realm.AuthorizingRealm;
import org.apache.shiro.subject.PrincipalCollection;
import org.eclipse.sisu.Description;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Authenticates a user id plus user token pass code presented as HTTP basic credentials.
 *
 * Any client that can send basic auth works unchanged: Docker, Maven, npm and curl all simply receive the token in
 * place of a password. Authorization is deliberately not handled here, so the user keeps exactly the roles their own
 * account has; this realm only establishes who they are.
 */
@Named(UserTokenRealm.NAME)
@Singleton
@Description("User Token Realm")
public class UserTokenRealm
    extends AuthorizingRealm
{
  // AuthorizingRealm keeps its own logger private, so declare one here
  private static final Logger log = LoggerFactory.getLogger(UserTokenRealm.class);

  public static final String NAME = "UserTokenRealm";

  /**
   * Request attribute set once a request has been authenticated with a user token.
   *
   * {@code UserTokenContentFilter} reads it to enforce "require user tokens for repository access". This mirrors
   * {@code BearerTokenRealm.IS_TOKEN_AUTH_KEY}, which marks bearer-token requests the same way.
   */
  public static final String IS_USER_TOKEN_AUTH_KEY = UserTokenRealm.class.getName() + ".IS_USER_TOKEN";

  private final UserTokenService userTokenService;

  private final UserPrincipalsHelper principalsHelper;

  private final Provider<ServletRequest> requestProvider;

  @Inject
  public UserTokenRealm(
      final UserTokenService userTokenService,
      final UserPrincipalsHelper principalsHelper,
      final Provider<ServletRequest> requestProvider)
  {
    this.userTokenService = checkNotNull(userTokenService);
    this.principalsHelper = checkNotNull(principalsHelper);
    this.requestProvider = checkNotNull(requestProvider);

    setName(NAME);

    // The pass code is looked up directly, so there is no stored hash for Shiro to compare against
    setCredentialsMatcher(new AllowAllCredentialsMatcher());

    // Token lookup is a local store read, so caching it would add staleness after a reset for no real gain
    setAuthenticationCachingEnabled(false);
    setAuthorizationCachingEnabled(false);
  }

  @Override
  public boolean supports(final AuthenticationToken token) {
    return token instanceof UsernamePasswordToken;
  }

  @Override
  protected AuthenticationInfo doGetAuthenticationInfo(final AuthenticationToken token)
      throws AuthenticationException
  {
    if (!(token instanceof UsernamePasswordToken)) {
      return null;
    }

    UsernamePasswordToken upToken = (UsernamePasswordToken) token;
    String username = upToken.getUsername();
    char[] passCode = upToken.getPassword();

    if (username == null || username.isBlank() || passCode == null || passCode.length == 0) {
      return null;
    }

    Optional<PrincipalCollection> owner = userTokenService.principalsFor(passCode);
    if (owner.isEmpty()) {
      // not a user token; let the password realms try
      return null;
    }

    PrincipalCollection principals = owner.get();
    String ownerId = principals.getPrimaryPrincipal().toString();

    // The token identifies its owner on its own, but a mismatched user name means the caller is confused about who
    // they are. Accepting it would let one user's token authenticate under another user's name.
    if (!ownerId.equals(username)) {
      log.debug("User token belongs to {} but was presented as {}", ownerId, username);
      return null;
    }

    if (!isActive(principals, ownerId)) {
      return null;
    }

    markTokenAuthenticated();

    log.debug("Authenticated {} with a user token", ownerId);
    return new SimpleAuthenticationInfo(principals, passCode);
  }

  /**
   * Flags the request so the content filter can tell token authentication from password authentication.
   *
   * There is no request bound outside of HTTP handling, for instance when a task authenticates, so a missing
   * provider value is normal and not an error.
   */
  private void markTokenAuthenticated() {
    try {
      ServletRequest request = requestProvider.get();
      if (request != null) {
        request.setAttribute(IS_USER_TOKEN_AUTH_KEY, Boolean.TRUE);
      }
    }
    catch (RuntimeException e) {
      log.debug("No request bound; skipping user token request marker", e);
    }
  }

  /**
   * Authorization is left to the realm that owns the account, so this realm contributes nothing.
   *
   * Returning {@code null} rather than an empty {@link AuthorizationInfo} keeps Shiro's modular authorizer polling
   * the other realms for the user's real roles.
   */
  @Override
  protected AuthorizationInfo doGetAuthorizationInfo(final PrincipalCollection principals) {
    return null;
  }

  /**
   * Rejects tokens whose owner has since been disabled or locked, which is the main reason to prefer a token over a
   * shared password: revocation follows the account.
   */
  private boolean isActive(final PrincipalCollection principals, final String ownerId) {
    try {
      if (principalsHelper.getUserStatus(principals).isActive()) {
        return true;
      }
      log.debug("User token owner {} is not active", ownerId);
    }
    catch (UserNotFoundException e) {
      log.debug("User token owner {} no longer exists", ownerId, e);
    }
    return false;
  }
}
