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

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.inject.Inject;
import javax.inject.Named;
import javax.inject.Singleton;

import org.sonatype.goodies.common.ComponentSupport;
import org.sonatype.nexus.coelacanth.usertoken.UserTokenSettings;
import org.sonatype.nexus.rapture.StateContributor;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Publishes the feature's state to the UI.
 *
 * The {@code usertoken} key and its {@code enabled} flag are the same shape the existing user menu entry in
 * nexus-coreui-plugin already tests through {@code statesEnabled}, so that menu item becomes visible once this
 * plugin's capability is active.
 */
@Named
@Singleton
public class UserTokenStateContributor
    extends ComponentSupport
    implements StateContributor
{
  private static final String STATE_KEY = "usertoken";

  private final UserTokenSettings settings;

  @Inject
  public UserTokenStateContributor(final UserTokenSettings settings) {
    this.settings = checkNotNull(settings);
  }

  @Override
  public Map<String, Object> getState() {
    Map<String, Object> state = new HashMap<>();
    state.put("enabled", settings.isEnabled());
    state.put("protectContent", settings.isProtectContent());
    return Collections.singletonMap(STATE_KEY, state);
  }
}
