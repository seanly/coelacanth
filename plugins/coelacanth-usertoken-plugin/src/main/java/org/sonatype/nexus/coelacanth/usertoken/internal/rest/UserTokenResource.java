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
package org.sonatype.nexus.coelacanth.usertoken.internal.rest;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.QueryParam;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.Response.Status;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenExpiredException;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenService;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenSettings;
import org.sonatype.nexus.common.wonderland.AuthTicketService;
import org.sonatype.nexus.rest.Resource;
import org.sonatype.nexus.rest.WebApplicationMessageException;

import org.apache.shiro.authz.annotation.RequiresAuthentication;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Lets a signed-in user read, generate and reset their own token.
 *
 * The paths and payload shapes here are fixed by the user token screen in nexus-coreui-plugin, which calls
 * {@code /service/rest/internal/current-user/user-token} with GET, POST and DELETE, and reads the expiry from
 * {@code .../user-token/attributes}.
 *
 * Every call except the attributes read requires a freshly redeemed authentication ticket on top of the session:
 * the token is password-equivalent, so holding a live session is not by itself enough to reveal it.
 */
@Named
@Singleton
@Path(UserTokenResource.RESOURCE_URI)
@Produces(MediaType.APPLICATION_JSON)
public class UserTokenResource
    extends ComponentSupport
    implements Resource
{
  public static final String RESOURCE_URI = "/internal/current-user/user-token";

  private final UserTokenService userTokenService;

  private final AuthTicketService authTicketService;

  private final UserTokenSettings settings;

  @Inject
  public UserTokenResource(
      final UserTokenService userTokenService,
      final AuthTicketService authTicketService,
      final UserTokenSettings settings)
  {
    this.userTokenService = checkNotNull(userTokenService);
    this.authTicketService = checkNotNull(authTicketService);
    this.settings = checkNotNull(settings);
  }

  /**
   * Returns the caller's token, creating one on first access.
   */
  @GET
  @RequiresAuthentication
  public UserTokenXO access(@QueryParam("authToken") final String authToken) {
    requireEnabled();
    redeem(authToken);

    try {
      return UserTokenXO.from(userTokenService.findOrCreateForCurrentUser());
    }
    catch (UserTokenExpiredException e) {
      // The screen matches on this text to offer "Generate" instead of "Access"
      throw new WebApplicationMessageException(Status.GONE, e.getMessage(), MediaType.TEXT_PLAIN);
    }
  }

  /**
   * Issues a replacement token. This is the path taken after the previous one expired.
   */
  @POST
  @RequiresAuthentication
  public UserTokenXO generate(@QueryParam("authToken") final String authToken) {
    requireEnabled();
    redeem(authToken);
    return UserTokenXO.from(userTokenService.generateForCurrentUser());
  }

  /**
   * Revokes the caller's token. The next access creates a fresh one.
   */
  @DELETE
  @RequiresAuthentication
  public Response reset(@QueryParam("authToken") final String authToken) {
    requireEnabled();
    redeem(authToken);
    userTokenService.revokeForCurrentUser();
    return Response.noContent().build();
  }

  /**
   * Reports when the caller's token expires.
   *
   * No ticket is needed: this exposes a timestamp, not the token, and the screen loads it on every render.
   */
  @GET
  @Path("/attributes")
  @RequiresAuthentication
  public UserTokenAttributesXO attributes() {
    requireEnabled();
    return userTokenService.expiryForCurrentUser()
        .map(expiry -> new UserTokenAttributesXO(Long.toString(expiry.toInstant().toEpochMilli())))
        .orElseGet(UserTokenAttributesXO::new);
  }

  private void requireEnabled() {
    if (!settings.isEnabled()) {
      throw new WebApplicationMessageException(Status.CONFLICT,
          "User tokens are not enabled. An administrator must enable the User Token capability.",
          MediaType.TEXT_PLAIN);
    }
  }

  /**
   * Consumes the one-shot ticket the UI obtained by re-prompting for the password.
   *
   * The ticket arrives base64-encoded to keep it clear of URL escaping issues, matching the convention the NuGet
   * API key screen already uses.
   */
  private void redeem(final String authToken) {
    if (authToken == null || authToken.isBlank()) {
      throw new WebApplicationMessageException(Status.FORBIDDEN, "Missing authentication ticket",
          MediaType.TEXT_PLAIN);
    }

    String ticket;
    try {
      ticket = new String(Base64.getDecoder().decode(authToken), StandardCharsets.UTF_8);
    }
    catch (IllegalArgumentException e) {
      log.debug("Malformed authentication ticket", e);
      throw new WebApplicationMessageException(Status.FORBIDDEN, "Malformed authentication ticket",
          MediaType.TEXT_PLAIN);
    }

    if (!authTicketService.redeemTicket(ticket)) {
      throw new WebApplicationMessageException(Status.FORBIDDEN, "Invalid authentication ticket",
          MediaType.TEXT_PLAIN);
    }
  }
}
