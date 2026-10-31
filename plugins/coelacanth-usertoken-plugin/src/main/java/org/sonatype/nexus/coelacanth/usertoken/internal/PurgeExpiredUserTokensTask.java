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

import javax.inject.Inject;
import javax.inject.Named;

import org.sonatype.nexus.coelacanth.usertoken.UserTokenService;
import org.sonatype.nexus.scheduling.Cancelable;
import org.sonatype.nexus.scheduling.TaskSupport;

import static com.google.common.base.Preconditions.checkNotNull;

/**
 * Deletes user tokens that have passed their expiry.
 *
 * Expired tokens are already refused at authentication time, so this is housekeeping rather than enforcement.
 */
@Named
public class PurgeExpiredUserTokensTask
    extends TaskSupport
    implements Cancelable
{
  private final UserTokenService userTokenService;

  @Inject
  public PurgeExpiredUserTokensTask(final UserTokenService userTokenService) {
    this.userTokenService = checkNotNull(userTokenService);
  }

  @Override
  protected Void execute() throws Exception {
    int deleted = userTokenService.purgeExpired();
    log.info("Purged {} expired user tokens", deleted);
    return null;
  }

  @Override
  public String getMessage() {
    return "Deleting expired user tokens";
  }
}
