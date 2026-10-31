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

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.security.UserPrincipalsHelper;
import org.sonatype.nexus.security.user.UserNotFoundException;
import org.sonatype.nexus.security.user.UserStatus;

import javax.servlet.ServletRequest;

import org.apache.shiro.authc.AuthenticationInfo;
import org.apache.shiro.authc.BearerToken;
import org.apache.shiro.authc.UsernamePasswordToken;
import org.apache.shiro.subject.PrincipalCollection;
import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.notNullValue;
import static org.hamcrest.CoreMatchers.nullValue;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserTokenRealmTest
    extends TestSupport
{
  private static final String USER = "jane";

  private static final char[] PASS_CODE = "s3cr3t-token".toCharArray();

  @Mock
  private UserTokenService userTokenService;

  @Mock
  private UserPrincipalsHelper principalsHelper;

  @Mock
  private ServletRequest servletRequest;

  private UserTokenRealm underTest;

  private PrincipalCollection principals;

  @Before
  public void setup() {
    underTest = new UserTokenRealm(userTokenService, principalsHelper, () -> servletRequest);
    principals = new SimplePrincipalCollection(USER, "NexusAuthenticatingRealm");
  }

  @Test
  public void authenticatesWhenTokenAndUserMatch() throws Exception {
    when(userTokenService.principalsFor(PASS_CODE)).thenReturn(Optional.of(principals));
    when(principalsHelper.getUserStatus(principals)).thenReturn(UserStatus.active);

    AuthenticationInfo info = underTest.doGetAuthenticationInfo(new UsernamePasswordToken(USER, PASS_CODE));

    assertThat(info, notNullValue());
    assertThat(info.getPrincipals().getPrimaryPrincipal(), is(USER));
  }

  /**
   * The marker is what tells the content filter this request used a token rather than a password.
   */
  @Test
  public void marksTheRequestOnTokenAuthentication() throws Exception {
    when(userTokenService.principalsFor(PASS_CODE)).thenReturn(Optional.of(principals));
    when(principalsHelper.getUserStatus(principals)).thenReturn(UserStatus.active);

    underTest.doGetAuthenticationInfo(new UsernamePasswordToken(USER, PASS_CODE));

    verify(servletRequest).setAttribute(UserTokenRealm.IS_USER_TOKEN_AUTH_KEY, Boolean.TRUE);
  }

  @Test
  public void doesNotMarkTheRequestWhenAuthenticationFails() {
    when(userTokenService.principalsFor(PASS_CODE)).thenReturn(Optional.empty());

    underTest.doGetAuthenticationInfo(new UsernamePasswordToken(USER, PASS_CODE));

    verify(servletRequest, never()).setAttribute(UserTokenRealm.IS_USER_TOKEN_AUTH_KEY, Boolean.TRUE);
  }

  @Test
  public void rejectsUnknownToken() {
    when(userTokenService.principalsFor(PASS_CODE)).thenReturn(Optional.empty());

    // returning null rather than throwing lets the password realms have their turn
    assertThat(underTest.doGetAuthenticationInfo(new UsernamePasswordToken(USER, PASS_CODE)), nullValue());
  }

  /**
   * The token alone identifies its owner, so a mismatched user name means the caller is not who they claim. Honouring
   * the token here would let one user authenticate under another user's name.
   */
  @Test
  public void rejectsTokenPresentedUnderAnotherUsersName() throws Exception {
    when(userTokenService.principalsFor(PASS_CODE)).thenReturn(Optional.of(principals));

    AuthenticationInfo info = underTest.doGetAuthenticationInfo(new UsernamePasswordToken("someone-else", PASS_CODE));

    assertThat(info, nullValue());
  }

  @Test
  public void rejectsTokenOfDisabledOwner() throws Exception {
    when(userTokenService.principalsFor(PASS_CODE)).thenReturn(Optional.of(principals));
    when(principalsHelper.getUserStatus(principals)).thenReturn(UserStatus.disabled);

    assertThat(underTest.doGetAuthenticationInfo(new UsernamePasswordToken(USER, PASS_CODE)), nullValue());
  }

  @Test
  public void rejectsTokenOfLockedOwner() throws Exception {
    when(userTokenService.principalsFor(PASS_CODE)).thenReturn(Optional.of(principals));
    when(principalsHelper.getUserStatus(principals)).thenReturn(UserStatus.locked);

    assertThat(underTest.doGetAuthenticationInfo(new UsernamePasswordToken(USER, PASS_CODE)), nullValue());
  }

  @Test
  public void rejectsTokenOfRemovedOwner() throws Exception {
    when(userTokenService.principalsFor(PASS_CODE)).thenReturn(Optional.of(principals));
    when(principalsHelper.getUserStatus(principals)).thenThrow(new UserNotFoundException(USER));

    assertThat(underTest.doGetAuthenticationInfo(new UsernamePasswordToken(USER, PASS_CODE)), nullValue());
  }

  @Test
  public void ignoresBlankUsername() {
    assertThat(underTest.doGetAuthenticationInfo(new UsernamePasswordToken("  ", PASS_CODE)), nullValue());
  }

  @Test
  public void ignoresEmptyPassword() {
    assertThat(underTest.doGetAuthenticationInfo(new UsernamePasswordToken(USER, new char[0])), nullValue());
  }

  @Test
  public void ignoresUnsupportedTokenType() {
    assertThat(underTest.supports(new BearerToken("abc")), is(false));
    assertThat(underTest.doGetAuthenticationInfo(new BearerToken("abc")), nullValue());
  }

  @Test
  public void supportsUsernamePasswordTokens() {
    assertThat(underTest.supports(new UsernamePasswordToken(USER, PASS_CODE)), is(true));
  }

  /**
   * Roles must come from the realm that owns the account, not from this one.
   */
  @Test
  public void contributesNoAuthorizationInfo() {
    assertThat(underTest.doGetAuthorizationInfo(principals), nullValue());
  }
}
