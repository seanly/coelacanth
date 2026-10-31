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

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.ws.rs.Consumes;
import javax.ws.rs.DELETE;
import javax.ws.rs.GET;
import javax.ws.rs.PUT;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.capability.CapabilityReference;
import org.sonatype.nexus.capability.CapabilityRegistry;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenService;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenSettings;
import org.sonatype.nexus.coelacanth.usertoken.internal.capability.UserTokenCapabilityConfiguration;
import org.sonatype.nexus.coelacanth.usertoken.internal.capability.UserTokenCapabilityDescriptor;
import org.sonatype.nexus.rest.Resource;
import org.sonatype.nexus.validation.Validate;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;

import static com.google.common.base.Preconditions.checkNotNull;
import static org.sonatype.nexus.capability.CapabilityReferenceFilterBuilder.capabilities;

/**
 * Administration endpoint for the user token settings.
 *
 * Reads and writes go through the capability registry rather than a store of their own, so this endpoint and the
 * capability administration screen are always two views of one configuration. Saving here creates the capability if
 * it does not exist yet, which is what lets an operator turn the feature on without visiting the capability screen.
 */
@Named
@Singleton
@Path(UserTokenConfigurationResource.RESOURCE_URI)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class UserTokenConfigurationResource
    extends ComponentSupport
    implements Resource
{
  public static final String RESOURCE_URI = "/v1/security/user-tokens";

  private final CapabilityRegistry capabilityRegistry;

  private final UserTokenSettings settings;

  private final UserTokenService userTokenService;

  @Inject
  public UserTokenConfigurationResource(
      final CapabilityRegistry capabilityRegistry,
      final UserTokenSettings settings,
      final UserTokenService userTokenService)
  {
    this.capabilityRegistry = checkNotNull(capabilityRegistry);
    this.settings = checkNotNull(settings);
    this.userTokenService = checkNotNull(userTokenService);
  }
  @GET
  @RequiresAuthentication
  @RequiresPermissions("nexus:settings:read")
  public UserTokenConfigurationXO read() {
    UserTokenConfigurationXO xo = new UserTokenConfigurationXO();
    xo.setEnabled(settings.isEnabled());
    xo.setProtectContent(settings.isProtectContent());
    xo.setExpirationEnabled(settings.isExpirationEnabled());
    xo.setExpirationDays(settings.getExpirationDays());
    return xo;
  }

  /**
   * Applies the settings, creating or updating the backing capability.
   *
   * Saving with {@code enabled} false disables the capability rather than deleting it, so the other settings survive
   * being switched off and on again.
   */
  @PUT
  @RequiresAuthentication
  @RequiresPermissions("nexus:settings:update")
  @Validate
  public UserTokenConfigurationXO update(final UserTokenConfigurationXO configuration) {
    Map<String, String> properties = new HashMap<>();
    properties.put(UserTokenCapabilityConfiguration.P_PROTECT_CONTENT,
        Boolean.toString(configuration.isProtectContent()));
    properties.put(UserTokenCapabilityConfiguration.P_EXPIRATION_ENABLED,
        Boolean.toString(configuration.isExpirationEnabled()));
    if (configuration.getExpirationDays() != null) {
      properties.put(UserTokenCapabilityConfiguration.P_EXPIRATION_DAYS,
          Integer.toString(configuration.getExpirationDays()));
    }

    Optional<CapabilityReference> existing = findCapability();
    if (existing.isPresent()) {
      capabilityRegistry.update(existing.get().context().id(), configuration.isEnabled(),
          existing.get().context().notes(), properties);
    }
    else {
      capabilityRegistry.add(UserTokenCapabilityDescriptor.TYPE, configuration.isEnabled(),
          "Created through the user tokens REST API", properties);
    }

    log.info("User token configuration updated: enabled={} protectContent={} expiration={}/{}d",
        configuration.isEnabled(), configuration.isProtectContent(), configuration.isExpirationEnabled(),
        configuration.getExpirationDays());

    return read();
  }

  /**
   * Revokes every issued token.
   *
   * This is the "Reset all user tokens" action. It does not change the configuration: the feature stays enabled and
   * users simply receive a new token on their next access.
   */
  @DELETE
  @RequiresAuthentication
  @RequiresPermissions("nexus:settings:update")
  public Response resetAll() {
    int revoked = userTokenService.revokeAll();
    log.warn("All user tokens revoked ({} removed)", revoked);
    return Response.noContent().build();
  }

  private Optional<CapabilityReference> findCapability() {
    Collection<? extends CapabilityReference> found =
        capabilityRegistry.get(capabilities().withType(UserTokenCapabilityDescriptor.TYPE));
    return found.stream().findFirst().map(CapabilityReference.class::cast);
  }
}
