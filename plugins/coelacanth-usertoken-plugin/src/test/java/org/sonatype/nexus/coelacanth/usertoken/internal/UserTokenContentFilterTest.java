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
package org.sonatype.nexus.coelacanth.usertoken.internal;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sonatype.goodies.testsupport.TestSupport;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenService;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenSettings;

import org.apache.shiro.subject.SimplePrincipalCollection;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

public class UserTokenContentFilterTest
    extends TestSupport
{
  @Mock
  private UserTokenService userTokenService;

  @Mock
  private HttpServletRequest request;

  @Mock
  private HttpServletResponse response;

  private UserTokenSettings settings;

  private UserTokenContentFilter underTest;

  @Before
  public void setup() throws Exception {
    settings = new UserTokenSettings();
    underTest = new UserTokenContentFilter(settings, userTokenService);
    when(response.getWriter()).thenReturn(new PrintWriter(new StringWriter()));
  }

  @Test
  public void allowsEverythingWhenDisabled() throws Exception {
    settings.setEnabled(false);
    settings.setProtectContent(true);

    assertThat(underTest.onPreHandle(request, response, null), is(true));
    verify(request, never()).getHeader(any());
  }

  @Test
  public void allowsEverythingWhenProtectionIsOff() throws Exception {
    settings.setEnabled(true);
    settings.setProtectContent(false);

    assertThat(underTest.onPreHandle(request, response, null), is(true));
    verify(request, never()).getHeader(any());
  }

  /**
   * Anonymous and other schemes present no basic password to replace, so they are left to the settings that govern
   * them.
   */
  @Test
  public void allowsRequestsWithoutBasicCredentials() throws Exception {
    protect();
    when(request.getHeader("Authorization")).thenReturn(null);

    assertThat(underTest.onPreHandle(request, response, null), is(true));
  }

  @Test
  public void allowsNonBasicSchemes() throws Exception {
    protect();
    when(request.getHeader("Authorization")).thenReturn("Bearer abc.def");

    assertThat(underTest.onPreHandle(request, response, null), is(true));
  }

  @Test
  public void allowsAValidUserToken() throws Exception {
    protect();
    when(request.getHeader("Authorization")).thenReturn(basic("jane", "a-real-token"));
    when(userTokenService.principalsFor("a-real-token".toCharArray()))
        .thenReturn(Optional.of(new SimplePrincipalCollection("jane", "UserTokenRealm")));

    assertThat(underTest.onPreHandle(request, response, null), is(true));
    verify(response, never()).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void refusesAPlainPassword() throws Exception {
    protect();
    when(request.getHeader("Authorization")).thenReturn(basic("jane", "her-actual-password"));
    when(userTokenService.principalsFor("her-actual-password".toCharArray())).thenReturn(Optional.empty());

    assertThat(underTest.onPreHandle(request, response, null), is(false));
    verify(response).setStatus(HttpServletResponse.SC_UNAUTHORIZED);
  }

  @Test
  public void allowsMalformedHeaderThrough() throws Exception {
    protect();
    when(request.getHeader("Authorization")).thenReturn("Basic !!!not-base64!!!");

    // not our decision to make; the authentication filters reject it
    assertThat(underTest.onPreHandle(request, response, null), is(true));
  }

  @Test
  public void allowsHeaderWithoutSeparatorThrough() throws Exception {
    protect();
    String encoded = Base64.getEncoder().encodeToString("nocolonhere".getBytes(StandardCharsets.UTF_8));
    when(request.getHeader("Authorization")).thenReturn("Basic " + encoded);

    assertThat(underTest.onPreHandle(request, response, null), is(true));
  }

  @Test
  public void schemeMatchIsCaseInsensitive() throws Exception {
    protect();
    when(request.getHeader("Authorization")).thenReturn(basic("jane", "tok").replace("Basic", "basic"));
    when(userTokenService.principalsFor("tok".toCharArray())).thenReturn(Optional.empty());

    assertThat(underTest.onPreHandle(request, response, null), is(false));
  }

  /**
   * A password containing a colon must survive intact, or legitimate tokens would be truncated.
   */
  @Test
  public void passwordMayContainColons() throws Exception {
    protect();
    when(request.getHeader("Authorization")).thenReturn(basic("jane", "a:b:c"));
    when(userTokenService.principalsFor("a:b:c".toCharArray()))
        .thenReturn(Optional.of(new SimplePrincipalCollection("jane", "UserTokenRealm")));

    assertThat(underTest.onPreHandle(request, response, null), is(true));
  }

  private void protect() {
    settings.setEnabled(true);
    settings.setProtectContent(true);
  }

  private String basic(final String user, final String password) {
    String encoded = Base64.getEncoder()
        .encodeToString((user + ':' + password).getBytes(StandardCharsets.UTF_8));
    return "Basic " + encoded;
  }
}
