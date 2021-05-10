/* **************************************************************************************
 * Copyright (c) 2020 Calypso Networks Association https://www.calypsonet-asso.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.eclipse.keyple.core.service.examples.UseCase4_ScheduledSelection;

import org.eclipse.keyple.core.service.*;
import org.eclipse.keyple.core.service.selection.CardSelectionService;
import org.eclipse.keyple.core.service.selection.spi.SmartCard;
import org.eclipse.keyple.core.service.spi.ReaderObservationExceptionHandlerSpi;
import org.eclipse.keyple.core.service.spi.ReaderObserverSpi;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * (package-private)<br>
 * Implements the reader observation SPIs.<br>
 * A reader Observer to handle card events such as CARD_INSERTED, CARD_MATCHED, CARD_REMOVED
 *
 * @since 2.0
 */
class CardReaderObserver implements ReaderObserverSpi, ReaderObservationExceptionHandlerSpi {

  private static final Logger logger = LoggerFactory.getLogger(CardReaderObserver.class);
  private final Reader reader;
  private final CardSelectionService selectionService;

  CardReaderObserver(Reader reader, CardSelectionService selectionService) {
    this.reader = reader;
    this.selectionService = selectionService;
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public void onReaderEvent(ReaderEvent event) {
    switch (event.getEventType()) {
      case CARD_MATCHED:
        // the selection has one target, get the result at index 0
        SmartCard smartCard =
            selectionService
                .parseScheduledCardSelectionsResponse(event.getScheduledCardSelectionsResponse())
                .getActiveSmartCard();

        logger.info(
            "Observer notification: the selection of the card has succeeded and return the SmartCard = {}.",
            smartCard);

        logger.info("= #### End of the card processing.");

        break;
      case CARD_INSERTED:
        logger.error(
            "CARD_INSERTED event: should not have occurred due to the MATCHED_ONLY selection mode.");
        break;
      case CARD_REMOVED:
        logger.trace("There is no card inserted anymore. Return to the waiting state...");
        break;
      default:
        break;
    }
    if (event.getEventType() == ReaderEvent.EventType.CARD_INSERTED
        || event.getEventType() == ReaderEvent.EventType.CARD_MATCHED) {

      // Informs the underlying layer of the end of the card processing, in order to manage the
      // removal sequence.

      ((ObservableReader) (reader)).finalizeCardProcessing();
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public void onReaderObservationError(String pluginName, String readerName, Throwable e) {
    logger.error("An exception occurred in plugin '{}', reader '{}'.", pluginName, readerName, e);
  }
}