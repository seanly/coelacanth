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
package org.sonatype.nexus.coelacanth.ldap.internal.rest;

import java.util.Optional;
import java.util.Set;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.directory.DirContext;
import javax.ws.rs.Consumes;
import javax.ws.rs.POST;
import javax.ws.rs.Path;
import javax.ws.rs.Produces;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response.Status;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.coelacanth.ldap.LdapConfiguration;
import org.sonatype.nexus.coelacanth.ldap.LdapConfigurationHolder;
import org.sonatype.nexus.coelacanth.ldap.LdapConnector;
import org.sonatype.nexus.coelacanth.ldap.LdapEntry;
import org.sonatype.nexus.coelacanth.ldap.GroupStrategyResolver;
import org.sonatype.nexus.rest.Resource;
import org.sonatype.nexus.rest.WebApplicationMessageException;

import org.apache.shiro.authz.annotation.RequiresAuthentication;
import org.apache.shiro.authz.annotation.RequiresPermissions;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Checks whether a directory configuration actually works.
 *
 * LDAP settings are easy to get subtly wrong, and the failure an operator sees without this is an unexplained login
 * rejection. Each step is reported separately so the response says which part is broken rather than only that
 * something is.
 */
@Named
@Singleton
@Path(LdapConnectionTestResource.RESOURCE_URI)
@Consumes(MediaType.APPLICATION_JSON)
@Produces(MediaType.APPLICATION_JSON)
public class LdapConnectionTestResource
    extends ComponentSupport
    implements Resource
{
  public static final String RESOURCE_URI = "/v1/security/ldap/connection-test";

  private final LdapConfigurationHolder configurationHolder;

  private final LdapConnector connector;

  private final GroupStrategyResolver groupStrategyResolver;

  @Inject
  public LdapConnectionTestResource(
      final LdapConfigurationHolder configurationHolder,
      final LdapConnector connector,
      final GroupStrategyResolver groupStrategyResolver)
  {
    this.configurationHolder = checkNotNull(configurationHolder);
    this.connector = checkNotNull(connector);
    this.groupStrategyResolver = checkNotNull(groupStrategyResolver);
  }
  @POST
  @RequiresAuthentication
  @RequiresPermissions("nexus:settings:update")
  public LdapConnectionTestXO test(final LdapConnectionTestRequestXO request) {
    LdapConfiguration config = resolve(request);
    LdapConnectionTestXO result = new LdapConnectionTestXO();

    DirContext context = null;
    try {
      context = connector.openManagerContext(config);
      result.setConnected(true);
      result.setBound(true);
    }
    catch (javax.naming.AuthenticationException e) {
      // reached the server, but the manager credentials were refused
      result.setConnected(true);
      result.setBound(false);
      result.setMessage("Connected to " + config.getLdapUrl() + " but the manager bind was rejected: "
          + e.getMessage());
      return result;
    }
    catch (Exception e) {
      result.setConnected(false);
      result.setBound(false);
      result.setMessage("Cannot reach " + config.getLdapUrl() + ": " + e.getMessage());
      return result;
    }
    finally {
      connector.close(context);
    }

    String testUserId = request == null ? null : request.getTestUserId();
    if (testUserId == null || testUserId.isBlank()) {
      result.setMessage("Connected to " + config.getLdapUrl() + " and bound successfully."
          + " Supply testUserId to also verify the user search filter.");
      return result;
    }

    return lookupUser(config, testUserId, result);
  }

  /**
   * Resolves the user and reports the groups that would be granted, which is what verifies the search filter and
   * group strategy rather than only connectivity.
   */
  private LdapConnectionTestXO lookupUser(
      final LdapConfiguration config,
      final String testUserId,
      final LdapConnectionTestXO result)
  {
    try {
      Optional<LdapEntry> entry = connector.findUser(config, testUserId);
      if (entry.isEmpty()) {
        result.setUserFound(false);
        result.setMessage("Connected and bound, but no entry matched " + testUserId
            + ". Check the search base and user search filter.");
        return result;
      }

      result.setUserFound(true);

      Set<String> groups = groupStrategyResolver.findGroups(config, entry.get());
      result.getGroups().addAll(groups);
      result.setGroupCount(groups.size());

      StringBuilder message = new StringBuilder()
          .append("Found ").append(entry.get().getDn());
      if (groups.isEmpty()) {
        message.append(" with no groups. ");
        message.append(describeEmptyGroups(config));
      }
      else {
        message.append(" in ").append(groups.size()).append(" group(s).");
      }
      result.setMessage(message.toString());
      return result;
    }
    catch (Exception e) {
      result.setUserFound(false);
      result.setMessage("Connected and bound, but the user search failed: " + e.getMessage());
      return result;
    }
  }

  /**
   * Explains the most common reason a user resolves but has no groups, which depends on the chosen strategy.
   */
  private String describeEmptyGroups(final LdapConfiguration config) {
    switch (config.getGroupStrategy()) {
      case NONE:
        return "Group mapping is disabled.";
      case DYNAMIC:
        return "Check that " + config.getUserMemberOfAttribute()
            + " is populated on the user entry; in OpenLDAP it requires the memberOf overlay.";
      case STATIC:
        return "Check the group search base, object class " + config.getGroupObjectClass() + " and member attribute "
            + config.getGroupMemberAttribute() + ".";
      case HIERARCHY:
        return "The user DN contains no ou components to derive groups from.";
      default:
        return "";
    }
  }

  /**
   * Builds the configuration to test: the active one, with any supplied fields overriding it.
   */
  private LdapConfiguration resolve(final LdapConnectionTestRequestXO request) {
    LdapConfiguration active = configurationHolder.get().orElse(null);

    if (request == null || isBlank(request.getLdapUrl())) {
      if (active == null) {
        throw new WebApplicationMessageException(Status.CONFLICT,
            "No LDAP configuration is active. Supply ldapUrl to test settings before saving them.",
            MediaType.TEXT_PLAIN);
      }
      if (request == null) {
        return active;
      }
      return merge(active, request);
    }

    // a URL was supplied, so build a standalone configuration from the request
    LdapConfiguration.Builder builder = LdapConfiguration.builder()
        .ldapUrl(request.getLdapUrl())
        .searchBase(request.getSearchBase())
        .managerDn(request.getManagerDn())
        .managerPassword(request.getManagerPassword())
        .userSearchFilter(request.getUserSearchFilter())
        .userIdAttribute(request.getUserIdAttribute());

    if (active != null) {
      // carry over the group settings so a test can report groups without restating them
      builder.groupStrategy(active.getGroupStrategy())
          .groupSearchBase(active.getGroupSearchBase())
          .groupObjectClass(active.getGroupObjectClass())
          .groupIdAttribute(active.getGroupIdAttribute())
          .groupMemberAttribute(active.getGroupMemberAttribute())
          .groupMemberFormat(active.getGroupMemberFormat())
          .userMemberOfAttribute(active.getUserMemberOfAttribute());
    }

    return builder.build();
  }

  private LdapConfiguration merge(final LdapConfiguration active, final LdapConnectionTestRequestXO request) {
    return LdapConfiguration.builder()
        .ldapUrl(active.getLdapUrl())
        .searchBase(isBlank(request.getSearchBase()) ? active.getSearchBase() : request.getSearchBase())
        .searchSubtree(active.isSearchSubtree())
        .userDnPattern(active.getUserDnPattern())
        .managerDn(isBlank(request.getManagerDn()) ? active.getManagerDn() : request.getManagerDn())
        .managerPassword(isBlank(request.getManagerPassword())
            ? active.getManagerPassword()
            : request.getManagerPassword())
        .userSearchFilter(isBlank(request.getUserSearchFilter())
            ? active.getUserSearchFilter()
            : request.getUserSearchFilter())
        .userIdAttribute(isBlank(request.getUserIdAttribute())
            ? active.getUserIdAttribute()
            : request.getUserIdAttribute())
        .emailAttribute(active.getEmailAttribute())
        .realNameAttribute(active.getRealNameAttribute())
        .groupStrategy(active.getGroupStrategy())
        .groupSearchBase(active.getGroupSearchBase())
        .groupObjectClass(active.getGroupObjectClass())
        .groupIdAttribute(active.getGroupIdAttribute())
        .groupMemberAttribute(active.getGroupMemberAttribute())
        .groupMemberFormat(active.getGroupMemberFormat())
        .userMemberOfAttribute(active.getUserMemberOfAttribute())
        .connectTimeoutMillis(active.getConnectTimeoutMillis())
        .readTimeoutMillis(active.getReadTimeoutMillis())
        .maxSearchResults(active.getMaxSearchResults())
        .referralStrategy(active.getReferralStrategy())
        .build();
  }

  private static boolean isBlank(final String value) {
    return value == null || value.isBlank();
  }
}
