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

import java.time.OffsetDateTime;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.security.SecurityHelper;
import org.sonatype.nexus.security.authc.apikey.ApiKey;
import org.sonatype.nexus.security.authc.apikey.ApiKeyLowLevelService;
import org.sonatype.nexus.security.authc.apikey.ApiKeyService;

import org.apache.shiro.subject.PrincipalCollection;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Creates, reads and revokes user tokens on top of the core {@link ApiKeyService}.
 *
 * All keys live in the {@link #DOMAIN} domain, which keeps them separate from the per-format bearer tokens that
 * other parts of the server store through the same service.
 */
@Named
@Singleton
public class UserTokenService
    extends ComponentSupport
{
  /**
   * API key domain owning every user token.
   */
  public static final String DOMAIN = "UserToken";

  private final ApiKeyService apiKeyService;

  private final SecurityHelper securityHelper;

  private final UserTokenSettings settings;

  @Inject
  public UserTokenService(
      final ApiKeyService apiKeyService,
      final SecurityHelper securityHelper,
      final UserTokenSettings settings)
  {
    this.apiKeyService = checkNotNull(apiKeyService);
    this.securityHelper = checkNotNull(securityHelper);
    this.settings = checkNotNull(settings);
  }

  /**
   * Returns the instant at which the given token expires, or empty when expiration is disabled.
   */
  public Optional<OffsetDateTime> expiresAt(final ApiKey key) {
    if (!settings.isExpirationEnabled() || key.getCreated() == null) {
      return Optional.empty();
    }
    return Optional.of(key.getCreated().plusDays(settings.getExpirationDays()));
  }

  /**
   * Whether the given token is past its expiry.
   *
   * An expired token is kept in the store rather than deleted, so the owner can be told it expired instead of
   * silently receiving a new one.
   */
  public boolean isExpired(final ApiKey key) {
    return expiresAt(key).map(expiry -> expiry.isBefore(OffsetDateTime.now())).orElse(false);
  }

  /**
   * Returns the current user's token expiry for the attributes endpoint, without requiring a step-up ticket.
   *
   * @return empty when the user has no token or expiration is disabled
   */
  public Optional<OffsetDateTime> expiryForCurrentUser() {
    return apiKeyService.getApiKey(DOMAIN, currentPrincipals()).flatMap(this::expiresAt);
  }

  /**
   * Returns the current user's token, creating one if they do not have it yet.
   *
   * @throws UserTokenExpiredException when a token exists but has expired; the owner has to generate a replacement
   *           explicitly so they learn that the old one stopped working
   */
  public UserToken findOrCreateForCurrentUser() {
    PrincipalCollection principals = currentPrincipals();
    Optional<ApiKey> existing = apiKeyService.getApiKey(DOMAIN, principals);

    if (existing.isPresent()) {
      ApiKey key = existing.get();
      if (isExpired(key)) {
        throw new UserTokenExpiredException(nameCodeOf(principals));
      }
      return new UserToken(nameCodeOf(principals), new String(key.getApiKey()), key.getCreated());
    }

    return createFor(principals);
  }

  /**
   * Issues a replacement token, discarding any existing one.
   *
   * This is what the owner of an expired token calls; unlike {@link #findOrCreateForCurrentUser()} it never reports
   * expiry back.
   */
  public UserToken generateForCurrentUser() {
    PrincipalCollection principals = currentPrincipals();
    apiKeyService.deleteApiKey(DOMAIN, principals);
    return createFor(principals);
  }

  private UserToken createFor(final PrincipalCollection principals) {
    char[] passCode = apiKeyService.createApiKey(DOMAIN, principals);
    log.info("Created user token for {}", nameCodeOf(principals));

    // Re-read so the stored creation timestamp is the one reported
    return apiKeyService.getApiKey(DOMAIN, principals)
        .map(key -> new UserToken(nameCodeOf(principals), new String(key.getApiKey()), key.getCreated()))
        .orElseGet(() -> new UserToken(nameCodeOf(principals), new String(passCode), null));
  }

  /**
   * Returns the current user's token without creating one.
   */
  public Optional<UserToken> findForCurrentUser() {
    PrincipalCollection principals = currentPrincipals();
    return apiKeyService.getApiKey(DOMAIN, principals)
        .map(key -> new UserToken(nameCodeOf(principals), new String(key.getApiKey()), key.getCreated()));
  }

  /**
   * Revokes the current user's token and issues a replacement.
   */
  public UserToken resetForCurrentUser() {
    PrincipalCollection principals = currentPrincipals();
    log.info("Reset user token for {}", nameCodeOf(principals));
    return generateForCurrentUser();
  }

  /**
   * Revokes the current user's token without issuing a replacement.
   *
   * @return {@code true} if a token existed
   */
  public boolean revokeForCurrentUser() {
    PrincipalCollection principals = currentPrincipals();
    if (apiKeyService.getApiKey(DOMAIN, principals).isEmpty()) {
      return false;
    }
    apiKeyService.deleteApiKey(DOMAIN, principals);
    log.info("Revoked user token for {}", nameCodeOf(principals));
    return true;
  }

  /**
   * Revokes every user token in the system. Used by the administrative reset.
   */
  public int revokeAll() {
    int deleted = apiKeyService.deleteApiKeys(DOMAIN);
    log.warn("Revoked all {} user tokens", deleted);
    return deleted;
  }

  /**
   * Resolves a submitted pass code to the principals it belongs to.
   *
   * @return empty when the token is unknown
   */
  public Optional<PrincipalCollection> principalsFor(final char[] passCode) {
    if (passCode == null || passCode.length == 0) {
      return Optional.empty();
    }

    return apiKeyService.getApiKeyByToken(DOMAIN, passCode)
        .filter(key -> {
          // Expiry has to be enforced here as well as in the UI, or an expired token would keep authenticating
          if (isExpired(key)) {
            log.debug("Rejecting expired user token for {}", key.getPrimaryPrincipal());
            return false;
          }
          return true;
        })
        .map(ApiKey::getPrincipals);
  }

  /**
   * Counts the issued tokens.
   */
  public int count() {
    return apiKeyService.count(DOMAIN);
  }

  /**
   * Deletes tokens that are past their expiry.
   *
   * Expired tokens are already refused at authentication time, so this only reclaims storage.
   *
   * Note that {@code ApiKeyService.deleteApiKeys(OffsetDateTime)} is deliberately NOT used here: it is not
   * domain-scoped and would also delete NuGet keys and any other domain's keys of the same age.
   *
   * @return the number deleted, or zero when expiration is disabled
   */
  public int purgeExpired() {
    if (!settings.isExpirationEnabled()) {
      return 0;
    }

    if (!(apiKeyService instanceof ApiKeyLowLevelService)) {
      log.debug("API key service does not support browsing; skipping expired token purge");
      return 0;
    }

    int deleted = 0;
    for (ApiKey key : ((ApiKeyLowLevelService) apiKeyService).browse(DOMAIN)) {
      if (isExpired(key)) {
        deleted += apiKeyService.deleteApiKey(DOMAIN, key.getPrincipals());
      }
    }

    if (deleted > 0) {
      log.info("Purged {} expired user tokens", deleted);
    }
    return deleted;
  }

  private PrincipalCollection currentPrincipals() {
    PrincipalCollection principals = securityHelper.subject().getPrincipals();
    checkNotNull(principals, "No authenticated subject");
    return principals;
  }

  private String nameCodeOf(final PrincipalCollection principals) {
    return principals.getPrimaryPrincipal().toString();
  }
}
