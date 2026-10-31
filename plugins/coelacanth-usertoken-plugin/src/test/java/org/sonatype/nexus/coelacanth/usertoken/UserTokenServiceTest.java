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

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.Optional;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.security.SecurityHelper;
import org.sonatype.nexus.security.authc.apikey.ApiKey;
import org.sonatype.nexus.security.authc.apikey.ApiKeyService;

import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.apache.shiro.subject.Subject;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserTokenServiceTest
    extends TestSupport
{
  private static final String USER = "jane";

  private static final char[] PASS_CODE = "generated-pass".toCharArray();

  @Mock
  private ApiKeyService apiKeyService;

  @Mock
  private SecurityHelper securityHelper;

  @Mock
  private Subject subject;

  @Mock
  private ApiKey apiKey;

  private UserTokenSettings settings;

  private UserTokenService underTest;

  private PrincipalCollection principals;

  @Before
  public void setup() {
    settings = new UserTokenSettings();
    settings.setEnabled(true);
    underTest = new UserTokenService(apiKeyService, securityHelper, settings);
    principals = new SimplePrincipalCollection(USER, "NexusAuthenticatingRealm");
    when(securityHelper.subject()).thenReturn(subject);
    when(subject.getPrincipals()).thenReturn(principals);
  }

  @Test
  public void returnsExistingTokenWithoutCreatingOne() {
    OffsetDateTime created = OffsetDateTime.now();
    when(apiKey.getApiKey()).thenReturn(PASS_CODE);
    when(apiKey.getCreated()).thenReturn(created);
    when(apiKeyService.getApiKey(UserTokenService.DOMAIN, principals)).thenReturn(Optional.of(apiKey));

    UserToken token = underTest.findOrCreateForCurrentUser();

    assertThat(token.getNameCode(), is(USER));
    assertThat(token.getPassCode(), is(new String(PASS_CODE)));
    assertThat(token.getCreated(), is(created));
    verify(apiKeyService, never()).createApiKey(eq(UserTokenService.DOMAIN), eq(principals));
  }

  @Test
  public void createsTokenOnFirstAccess() {
    when(apiKeyService.getApiKey(UserTokenService.DOMAIN, principals))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(apiKey));
    when(apiKeyService.createApiKey(UserTokenService.DOMAIN, principals)).thenReturn(PASS_CODE);
    when(apiKey.getApiKey()).thenReturn(PASS_CODE);
    when(apiKey.getCreated()).thenReturn(null);

    UserToken token = underTest.findOrCreateForCurrentUser();

    assertThat(token.getPassCode(), is(new String(PASS_CODE)));
    verify(apiKeyService).createApiKey(UserTokenService.DOMAIN, principals);
  }

  @Test
  public void findDoesNotCreate() {
    when(apiKeyService.getApiKey(UserTokenService.DOMAIN, principals)).thenReturn(Optional.empty());

    assertThat(underTest.findForCurrentUser().isPresent(), is(false));
    verify(apiKeyService, never()).createApiKey(eq(UserTokenService.DOMAIN), eq(principals));
  }

  @Test
  public void resetDeletesThenRecreates() {
    when(apiKeyService.getApiKey(UserTokenService.DOMAIN, principals))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(apiKey));
    when(apiKeyService.createApiKey(UserTokenService.DOMAIN, principals)).thenReturn(PASS_CODE);
    when(apiKey.getApiKey()).thenReturn(PASS_CODE);

    underTest.resetForCurrentUser();

    verify(apiKeyService).deleteApiKey(UserTokenService.DOMAIN, principals);
    verify(apiKeyService).createApiKey(UserTokenService.DOMAIN, principals);
  }

  @Test
  public void revokeReportsWhetherTokenExisted() {
    when(apiKeyService.getApiKey(UserTokenService.DOMAIN, principals)).thenReturn(Optional.of(apiKey));
    assertThat(underTest.revokeForCurrentUser(), is(true));
    verify(apiKeyService).deleteApiKey(UserTokenService.DOMAIN, principals);
  }

  @Test
  public void revokeIsNoOpWithoutToken() {
    when(apiKeyService.getApiKey(UserTokenService.DOMAIN, principals)).thenReturn(Optional.empty());

    assertThat(underTest.revokeForCurrentUser(), is(false));
    verify(apiKeyService, never()).deleteApiKey(UserTokenService.DOMAIN, principals);
  }

  @Test
  public void resolvesPrincipalsFromPassCode() {
    when(apiKey.getPrincipals()).thenReturn(principals);
    when(apiKeyService.getApiKeyByToken(UserTokenService.DOMAIN, PASS_CODE)).thenReturn(Optional.of(apiKey));

    assertThat(underTest.principalsFor(PASS_CODE).orElse(null), is(principals));
  }

  @Test
  public void emptyPassCodeResolvesToNothing() {
    assertThat(underTest.principalsFor(new char[0]).isPresent(), is(false));
    assertThat(underTest.principalsFor(null).isPresent(), is(false));
  }

  /**
   * Expiry has to be enforced at authentication time, not only in the UI, or an expired token keeps working.
   */
  @Test
  public void expiredTokenDoesNotAuthenticate() {
    settings.setExpirationEnabled(true);
    settings.setExpirationDays(30);
    when(apiKey.getCreated()).thenReturn(OffsetDateTime.now().minusDays(31));
    when(apiKeyService.getApiKeyByToken(UserTokenService.DOMAIN, PASS_CODE)).thenReturn(Optional.of(apiKey));

    assertThat(underTest.principalsFor(PASS_CODE).isPresent(), is(false));
  }

  @Test
  public void unexpiredTokenStillAuthenticates() {
    settings.setExpirationEnabled(true);
    settings.setExpirationDays(30);
    when(apiKey.getCreated()).thenReturn(OffsetDateTime.now().minusDays(29));
    when(apiKey.getPrincipals()).thenReturn(principals);
    when(apiKeyService.getApiKeyByToken(UserTokenService.DOMAIN, PASS_CODE)).thenReturn(Optional.of(apiKey));

    assertThat(underTest.principalsFor(PASS_CODE).orElse(null), is(principals));
  }

  @Test
  public void ageIsIrrelevantWhenExpirationIsDisabled() {
    settings.setExpirationEnabled(false);
    when(apiKey.getCreated()).thenReturn(OffsetDateTime.now().minusYears(5));
    when(apiKey.getPrincipals()).thenReturn(principals);
    when(apiKeyService.getApiKeyByToken(UserTokenService.DOMAIN, PASS_CODE)).thenReturn(Optional.of(apiKey));

    assertThat(underTest.principalsFor(PASS_CODE).orElse(null), is(principals));
  }

  @Test
  public void accessingAnExpiredTokenReportsExpiry() {
    settings.setExpirationEnabled(true);
    settings.setExpirationDays(30);
    when(apiKey.getCreated()).thenReturn(OffsetDateTime.now().minusDays(31));
    when(apiKeyService.getApiKey(UserTokenService.DOMAIN, principals)).thenReturn(Optional.of(apiKey));

    try {
      underTest.findOrCreateForCurrentUser();
      throw new AssertionError("expected UserTokenExpiredException");
    }
    catch (UserTokenExpiredException e) {
      // the front end matches on this text to offer "Generate" instead of "Access"
      assertThat(e.getMessage(), is("User Token expired"));
    }
  }

  @Test
  public void generateReplacesAnExpiredToken() {
    settings.setExpirationEnabled(true);
    settings.setExpirationDays(30);
    when(apiKeyService.getApiKey(UserTokenService.DOMAIN, principals))
        .thenReturn(Optional.empty())
        .thenReturn(Optional.of(apiKey));
    when(apiKeyService.createApiKey(UserTokenService.DOMAIN, principals)).thenReturn(PASS_CODE);
    when(apiKey.getApiKey()).thenReturn(PASS_CODE);
    when(apiKey.getCreated()).thenReturn(OffsetDateTime.now());

    UserToken token = underTest.generateForCurrentUser();

    assertThat(token.getPassCode(), is(new String(PASS_CODE)));
    verify(apiKeyService).deleteApiKey(UserTokenService.DOMAIN, principals);
    verify(apiKeyService).createApiKey(UserTokenService.DOMAIN, principals);
  }

  @Test
  public void reportsExpiryTimestamp() {
    settings.setExpirationEnabled(true);
    settings.setExpirationDays(10);
    OffsetDateTime created = OffsetDateTime.now().minusDays(1);
    when(apiKey.getCreated()).thenReturn(created);
    when(apiKeyService.getApiKey(UserTokenService.DOMAIN, principals)).thenReturn(Optional.of(apiKey));

    assertThat(underTest.expiryForCurrentUser().orElse(null), is(created.plusDays(10)));
  }

  @Test
  public void reportsNoExpiryWhenDisabled() {
    settings.setExpirationEnabled(false);
    when(apiKeyService.getApiKey(UserTokenService.DOMAIN, principals)).thenReturn(Optional.of(apiKey));

    assertThat(underTest.expiryForCurrentUser().isPresent(), is(false));
  }

  @Test
  public void purgeIsSkippedWhenExpirationIsDisabled() {
    settings.setExpirationEnabled(false);

    assertThat(underTest.purgeExpired(), is(0));
  }

  @Test
  public void expirationDaysAreClamped() {
    settings.setExpirationDays(0);
    assertThat(settings.getExpirationDays(), is(1));

    settings.setExpirationDays(5000);
    assertThat(settings.getExpirationDays(), is(999));

    settings.setExpirationDays(null);
    assertThat(settings.getExpirationDays(), is(UserTokenSettings.DEFAULT_EXPIRATION_DAYS));
  }

  @Test
  public void tokensAreScopedToTheirOwnDomain() {
    // keeps user tokens separate from the per-format bearer tokens stored through the same service
    assertThat(UserTokenService.DOMAIN, is("UserToken"));
  }

  @Test
  public void encodesBasicAuthPair() {
    UserToken token = new UserToken(USER, "pass", null);

    String decoded = new String(Base64.getDecoder().decode(token.encodeBasicAuth()), StandardCharsets.UTF_8);

    assertThat(decoded, is("jane:pass"));
  }

  @Test
  public void toStringOmitsPassCode() {
    assertThat(new UserToken(USER, "topsecret", null).toString().contains("topsecret"), is(false));
  }
}
