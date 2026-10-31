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

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.sonatype.nexus.coelacanth.usertoken.UserTokenService;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenSettings;

import org.apache.shiro.web.filter.PathMatchingFilter;
import org.apache.shiro.web.util.WebUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Enforces "require user tokens for repository access".
 *
 * When the setting is on, a repository request carrying basic credentials is admitted only if the password is a
 * valid user token; a real password is refused.
 *
 * The check reads the {@code Authorization} header directly rather than inspecting the authenticated subject. That
 * costs one token lookup per credentialed request, but it makes the filter correct wherever it sits in the chain:
 * had it relied on the subject being authenticated already, running before the authentication filters would have
 * silently admitted everything, and the order in which filter chains contributed by different bundles are applied
 * is not guaranteed.
 *
 * Requests without basic credentials pass through untouched. Anonymous access is governed by its own setting, and
 * other credential kinds (NuGet API keys, bearer tokens) are themselves tokens rather than passwords.
 */
@Named(UserTokenContentFilter.NAME)
@Singleton
public class UserTokenContentFilter
    extends PathMatchingFilter
{
  private static final Logger log = LoggerFactory.getLogger(UserTokenContentFilter.class);

  public static final String NAME = "nx-coelacanth-usertoken-content";

  private static final String AUTHORIZATION = "Authorization";

  private static final String BASIC_PREFIX = "Basic ";

  private static final String MESSAGE = "A user token is required to access repository content."
      + " Generate one from the User Token screen and use it in place of your password.";

  private final UserTokenSettings settings;

  private final UserTokenService userTokenService;

  @Inject
  public UserTokenContentFilter(final UserTokenSettings settings, final UserTokenService userTokenService) {
    this.settings = checkNotNull(settings);
    this.userTokenService = checkNotNull(userTokenService);
  }

  @Override
  protected boolean onPreHandle(
      final ServletRequest request,
      final ServletResponse response,
      final Object mappedValue) throws IOException
  {
    if (!settings.isEnabled() || !settings.isProtectContent()) {
      return true;
    }

    char[] password = basicAuthPassword(WebUtils.toHttp(request));
    if (password == null) {
      // no basic credentials: anonymous, or some other scheme the authentication filters already handled
      return true;
    }

    if (userTokenService.principalsFor(password).isPresent()) {
      return true;
    }

    log.debug("Refusing password-authenticated repository request while user tokens are required");
    return reject(response);
  }

  /**
   * Extracts the password half of a basic {@code Authorization} header.
   *
   * @return {@code null} when the header is absent, not basic, or unparseable
   */
  private char[] basicAuthPassword(final HttpServletRequest request) {
    String header = request.getHeader(AUTHORIZATION);
    if (header == null || !header.regionMatches(true, 0, BASIC_PREFIX, 0, BASIC_PREFIX.length())) {
      return null;
    }

    try {
      String decoded = new String(Base64.getDecoder().decode(header.substring(BASIC_PREFIX.length()).trim()),
          StandardCharsets.UTF_8);
      int separator = decoded.indexOf(':');
      if (separator < 0 || separator == decoded.length() - 1) {
        return null;
      }
      return decoded.substring(separator + 1).toCharArray();
    }
    catch (IllegalArgumentException e) {
      log.debug("Unparseable basic Authorization header", e);
      return null;
    }
  }

  private boolean reject(final ServletResponse response) throws IOException {
    HttpServletResponse http = WebUtils.toHttp(response);
    http.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
    http.setContentType("text/plain");
    http.getWriter().write(MESSAGE);
    http.getWriter().flush();
    return false;
  }
}
