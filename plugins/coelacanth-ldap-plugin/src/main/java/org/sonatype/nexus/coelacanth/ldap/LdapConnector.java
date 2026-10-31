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

import java.util.ArrayList;
import java.util.Hashtable;
import java.util.List;
import java.util.Optional;

import javax.annotation.Nullable;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.naming.AuthenticationException;
import javax.naming.Context;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.Attribute;
import javax.naming.directory.Attributes;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import javax.naming.directory.SearchControls;
import javax.naming.directory.SearchResult;

import org.sonatype.goodies.common.ComponentSupport;

/**
 * Performs the raw directory operations: binding, searching and reading attributes.
 *
 * Everything here is stateless; the caller supplies the {@link LdapConfiguration} on each call. Connections are
 * opened per operation and always closed, which keeps the realm safe to call concurrently without pooling logic of
 * its own. Timeouts are always set, because JNDI defaults to waiting forever and an unresponsive directory would
 * otherwise pin every request thread in the server.
 */
@Named
@Singleton
public class LdapConnector
    extends ComponentSupport
{
  private static final String LDAP_CTX_FACTORY = "com.sun.jndi.ldap.LdapCtxFactory";

  /**
   * Opens a context bound as the manager account, or anonymously when no manager DN is configured.
   */
  public DirContext openManagerContext(final LdapConfiguration config) throws NamingException {
    if (config.getManagerDn() == null || config.getManagerDn().isBlank()) {
      return open(config, null, null);
    }
    return open(config, config.getManagerDn(), config.getManagerPassword());
  }

  /**
   * Opens a context bound as the given principal. Used both for the manager bind and to verify user credentials.
   */
  public DirContext open(final LdapConfiguration config, final String principal, final String credentials)
      throws NamingException
  {
    Hashtable<String, String> env = new Hashtable<>();
    env.put(Context.INITIAL_CONTEXT_FACTORY, LDAP_CTX_FACTORY);
    env.put(Context.PROVIDER_URL, config.getLdapUrl());
    env.put(Context.REFERRAL, config.getReferralStrategy());

    // JNDI has no default timeout; without these a hung directory hangs the calling thread indefinitely
    env.put("com.sun.jndi.ldap.connect.timeout", Integer.toString(config.getConnectTimeoutMillis()));
    env.put("com.sun.jndi.ldap.read.timeout", Integer.toString(config.getReadTimeoutMillis()));

    if (principal == null) {
      env.put(Context.SECURITY_AUTHENTICATION, "none");
    }
    else {
      env.put(Context.SECURITY_AUTHENTICATION, "simple");
      env.put(Context.SECURITY_PRINCIPAL, principal);
      env.put(Context.SECURITY_CREDENTIALS, credentials == null ? "" : credentials);
    }

    if (config.isSecure()) {
      env.put(Context.SECURITY_PROTOCOL, "ssl");
    }

    return new InitialDirContext(env);
  }

  /**
   * Verifies a user's credentials by binding as that user.
   *
   * @return {@code true} when the bind succeeds, {@code false} when the directory rejects the credentials
   * @throws NamingException if the directory cannot be reached, which must not be reported as a bad password
   */
  public boolean authenticate(final LdapConfiguration config, final String userDn, final String password)
      throws NamingException
  {
    // An empty password would be an unauthenticated bind, which many directories accept outright
    if (password == null || password.isEmpty()) {
      return false;
    }

    DirContext context = null;
    try {
      context = open(config, userDn, password);
      return true;
    }
    catch (AuthenticationException e) {
      log.debug("Bind rejected for {}", userDn, e);
      return false;
    }
    finally {
      close(context);
    }
  }
  /**
   * Resolves a login name to a directory entry.
   *
   * In search-bind mode the manager account searches for the entry; in direct-bind mode the DN is built from the
   * configured pattern and the entry is read directly.
   */
  public Optional<LdapEntry> findUser(final LdapConfiguration config, final String username) throws NamingException {
    if (username == null || username.isBlank()) {
      return Optional.empty();
    }

    if (!config.isSearchBind()) {
      return Optional.of(buildEntryFromPattern(config, username));
    }

    DirContext context = null;
    try {
      context = openManagerContext(config);
      return searchUser(context, config, username);
    }
    finally {
      close(context);
    }
  }

  /**
   * Searches for a user entry using an already-open context, so callers that need several lookups can share one
   * connection.
   */
  public Optional<LdapEntry> searchUser(
      final DirContext context,
      final LdapConfiguration config,
      final String username) throws NamingException
  {
    String filter = userFilter(config, username);
    String base = searchBaseOf(config);

    SearchControls controls = new SearchControls();
    controls.setSearchScope(config.isSearchSubtree() ? SearchControls.SUBTREE_SCOPE : SearchControls.ONELEVEL_SCOPE);
    controls.setCountLimit(2); // a second hit means the filter is ambiguous
    controls.setTimeLimit(config.getReadTimeoutMillis());

    NamingEnumeration<SearchResult> results = null;
    try {
      results = context.search(base, filter, controls);
      if (!results.hasMore()) {
        log.debug("No directory entry matched filter {} under {}", filter, base);
        return Optional.empty();
      }

      SearchResult result = results.next();
      String dn = absoluteDn(result, base);

      if (results.hasMore()) {
        log.warn("Filter {} under {} matched more than one entry; refusing ambiguous match", filter, base);
        return Optional.empty();
      }

      return Optional.of(new LdapEntry(dn, username, result.getAttributes()));
    }
    finally {
      closeEnumeration(results);
    }
  }

  /**
   * Lists user entries, optionally restricted to those whose id starts with {@code prefix}.
   *
   * Used by the user manager to populate the user administration screen. Results are capped by
   * {@link LdapConfiguration#getMaxSearchResults()} so a large directory cannot exhaust the server.
   */
  public List<LdapEntry> listUsers(final LdapConfiguration config, @Nullable final String prefix, final int limit)
      throws NamingException
  {
    List<LdapEntry> entries = new ArrayList<>();
    String filter = listFilter(config, prefix);
    String base = searchBaseOf(config);

    SearchControls controls = new SearchControls();
    controls.setSearchScope(config.isSearchSubtree() ? SearchControls.SUBTREE_SCOPE : SearchControls.ONELEVEL_SCOPE);
    controls.setCountLimit(limit);
    controls.setTimeLimit(config.getReadTimeoutMillis());

    DirContext context = null;
    NamingEnumeration<SearchResult> results = null;
    try {
      context = openManagerContext(config);
      results = context.search(base, filter, controls);
      while (results.hasMore() && entries.size() < limit) {
        SearchResult result = results.next();
        Attributes attributes = result.getAttributes();
        Optional<String> userId = readFirst(attributes, config.getUserIdAttribute());
        if (userId.isPresent()) {
          entries.add(new LdapEntry(absoluteDn(result, base), userId.get(), attributes));
        }
      }
    }
    catch (javax.naming.SizeLimitExceededException e) {
      log.debug("Directory returned more than {} entries; results truncated", limit);
    }
    finally {
      closeEnumeration(results);
      close(context);
    }
    return entries;
  }

  /**
   * Builds the filter used to list users, escaping any caller-supplied prefix.
   */
  private String listFilter(final LdapConfiguration config, @Nullable final String prefix) {
    String idAttribute = config.getUserIdAttribute();
    if (prefix == null || prefix.isBlank()) {
      return "(" + idAttribute + "=*)";
    }
    // the trailing wildcard is ours, so it must sit outside the escaped value
    return "(" + idAttribute + "=" + LdapFilters.escapeFilterValue(prefix) + "*)";
  }

  /**
   * Reads a single attribute's values from an entry.
   */
  public List<String> readAttribute(final Attributes attributes, final String name) {
    List<String> values = new ArrayList<>();
    if (attributes == null || name == null) {
      return values;
    }

    Attribute attribute = attributes.get(name);
    if (attribute == null) {
      return values;
    }

    try {
      NamingEnumeration<?> all = attribute.getAll();
      while (all.hasMore()) {
        Object value = all.next();
        if (value != null) {
          values.add(value.toString());
        }
      }
    }
    catch (NamingException e) {
      log.debug("Unable to read attribute {}", name, e);
    }
    return values;
  }

  /**
   * Reads the first value of an attribute, if present.
   */
  public Optional<String> readFirst(final Attributes attributes, final String name) {
    List<String> values = readAttribute(attributes, name);
    return values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
  }

  /**
   * Builds the user search filter, escaping the untrusted login name.
   */
  public String userFilter(final LdapConfiguration config, final String username) {
    String configured = config.getUserSearchFilter();
    if (configured != null && !configured.isBlank()) {
      return LdapFilters.interpolate(configured, username, false);
    }
    return "(" + config.getUserIdAttribute() + "=" + LdapFilters.escapeFilterValue(username) + ")";
  }

  /**
   * Resolves the search base, which may be empty when the base DN is carried by the provider URL.
   */
  public String searchBaseOf(final LdapConfiguration config) {
    return config.getSearchBase() == null ? "" : config.getSearchBase();
  }

  public void close(final DirContext context) {
    if (context != null) {
      try {
        context.close();
      }
      catch (NamingException e) {
        log.debug("Failed to close directory context", e);
      }
    }
  }

  public void closeEnumeration(final NamingEnumeration<?> enumeration) {
    if (enumeration != null) {
      try {
        enumeration.close();
      }
      catch (NamingException e) {
        log.debug("Failed to close search enumeration", e);
      }
    }
  }

  private LdapEntry buildEntryFromPattern(final LdapConfiguration config, final String username) {
    String relative = LdapFilters.interpolate(config.getUserDnPattern(), username, true);
    String base = searchBaseOf(config);
    String dn = base.isEmpty() || relative.endsWith(base) ? relative : relative + "," + base;
    return new LdapEntry(dn, username, null);
  }

  /**
   * JNDI returns names relative to the search base, so rebuild the absolute DN before handing it onward.
   */
  private String absoluteDn(final SearchResult result, final String base) {
    if (result.isRelative() && !base.isEmpty()) {
      String name = result.getName();
      return name.isEmpty() ? base : name + "," + base;
    }
    return result.getNameInNamespace();
  }
}
