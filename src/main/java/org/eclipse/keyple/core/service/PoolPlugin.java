/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.core.service;

import java.util.SortedSet;

/**
 * Plugin able to handle the access to an undefined number of {@link Reader}.
 *
 * <p>It is typically used to define a plugin built on top of an HSM interface that can allocate a
 * large number of virtual reader slots.
 *
 * <p>A PoolPlugin can't be observable.
 *
 * @since 2.0
 */
public interface PoolPlugin extends Plugin {

  /**
   * Gets a list of group references that identify a group of readers.
   *
   * <p>A group reference can represent a family of readers that all have the same characteristics
   * (for example, containing a SAM with identical key sets).
   *
   * @return a list of String
   * @since 2.0
   */
  SortedSet<String> getReaderGroupReferences();

  /**
   * Gets a {@link Reader} and makes it exclusive to the caller until the {@link
   * #releaseReader(Reader)} method is invoked.
   *
   * <p>The allocated reader belongs to the group targeted with provided reference.
   *
   * @param readerGroupReference The reference of the group to which the reader belongs (may be null
   *     depending on the implementation made).
   * @return A not null reference.
   * @throws KeyplePluginException If the allocation failed due to lack of available reader.
   * @since 2.0
   */
  Reader allocateReader(String readerGroupReference);

  /**
   * Releases a Reader previously allocated with allocateReader.
   *
   * <p>This method must be invoked as soon as the reader is no longer needed by the caller of
   * allocateReader in order to free the resource.
   *
   * @param reader The Reader to be released.
   * @since 2.0
   */
  void releaseReader(Reader reader);
}
