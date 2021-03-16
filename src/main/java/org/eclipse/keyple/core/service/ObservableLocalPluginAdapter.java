/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://www.calypsonet-asso.org/
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

import java.util.Collection;
import java.util.Map;
import java.util.Set;
import java.util.SortedSet;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentSkipListSet;
import org.eclipse.keyple.core.plugin.ReaderIOException;
import org.eclipse.keyple.core.plugin.spi.ObservablePluginSpi;
import org.eclipse.keyple.core.plugin.spi.reader.ReaderSpi;
import org.eclipse.keyple.core.service.spi.PluginObserverSpi;
import org.eclipse.keyple.core.util.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * (package-private)<br>
 * Implementation of {@link ObservablePlugin} for a local plugin.
 *
 * @since 2.0
 */
final class ObservableLocalPluginAdapter
    extends AbstractObservablePluginAdapter<ObservablePluginSpi> {

  private static final Logger logger = LoggerFactory.getLogger(ObservableLocalPluginAdapter.class);

  private final ObservablePluginSpi observablePluginSpi;
  private final Map<String, Reader> readers;

  /**
   * (package-private)<br>
   * Creates an instance of {@link ObservableLocalPluginAdapter}.
   *
   * @param observablePluginSpi The plugin SPI.
   * @since 2.0
   */
  ObservableLocalPluginAdapter(ObservablePluginSpi observablePluginSpi) {
    super(observablePluginSpi);
    this.observablePluginSpi = observablePluginSpi;
    readers = new ConcurrentHashMap<String, Reader>();
  }

  /**
   * (package-private)<br>
   * Check whether the background job is monitoring for new readers
   *
   * @return true, if the background job is monitoring, false in all other cases.
   * @since 2.0
   */
  Boolean isMonitoring() {
    return thread != null && thread.isAlive() && thread.isMonitoring();
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public void addObserver(PluginObserverSpi observer) {
    Assert.getInstance().notNull(observer, "observer");

    super.addObserver(observer);
    if (countObservers() == 1) {
      if (logger.isDebugEnabled()) {
        logger.debug("Start monitoring the plugin {}", getName());
      }
      thread = new EventThread(getName());
      thread.setName("PluginEventMonitoringThread");
      thread.setUncaughtExceptionHandler(
          new Thread.UncaughtExceptionHandler() {
            public void uncaughtException(Thread t, Throwable e) {
              getObservationExceptionHandler().onPluginObservationError(thread.pluginName, e);
            }
          });
      thread.start();
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public void removeObserver(PluginObserverSpi observer) {
    super.removeObserver(observer);
    if (countObservers() == 0) {
      if (logger.isDebugEnabled()) {
        logger.debug("Stop the plugin monitoring.");
      }
      if (thread != null) {
        thread.end();
      }
    }
  }

  /**
   * {@inheritDoc}
   *
   * @since 2.0
   */
  @Override
  public void clearObservers() {
    super.clearObservers();
    if (thread != null) {
      if (logger.isDebugEnabled()) {
        logger.debug("Stop the plugin monitoring.");
      }
      thread.end();
    }
  }

  /** Local thread to monitoring readers presence */
  private EventThread thread;

  /** Thread in charge of reporting live events */
  private class EventThread extends Thread {
    private final String pluginName;
    private final long monitoringCycleDuration;
    private boolean running = true;

    private EventThread(String pluginName) {
      this.pluginName = pluginName;
      monitoringCycleDuration = observablePluginSpi.getMonitoringCycleDuration();
    }

    /** Marks the thread as one that should end when the last threadWaitTimeout occurs */
    private void end() {
      running = false;
      interrupt();
    }

    /**
     * (private)<br>
     * Indicate whether the thread is running or not
     */
    private boolean isMonitoring() {
      return running;
    }

    /**
     * (private)<br>
     * Adds a reader to the list of known readers (by the plugin)
     */
    private void addReader(String readerName) throws ReaderIOException {
      ReaderSpi readerSpi;
      readerSpi = observablePluginSpi.searchReader(readerName);
      LocalReaderAdapter reader = new LocalReaderAdapter(readerSpi, readerName);
      reader.register();
      readers.put(reader.getName(), reader);
      if (logger.isTraceEnabled()) {
        logger.trace(
            "[{}][{}] Plugin thread => Add plugged reader to readers list.",
            pluginName,
            reader.getName());
      }
    }

    /**
     * (private)<br>
     * Removes a reader from the list of known readers (by the plugin)
     */
    private void removeReader(Reader reader) {
      ((LocalReaderAdapter) reader).unregister();
      readers.remove(reader.getName());
      if (logger.isTraceEnabled()) {
        logger.trace(
            "[{}][{}] Plugin thread => Remove unplugged reader from readers list.",
            pluginName,
            reader.getName());
      }
    }

    /**
     * (private)<br>
     * Notifies observers of changes in the list of readers
     */
    private void notifyChanges(
        PluginEvent.EventType eventType, SortedSet<String> changedReaderNames) {
      /* grouped notification */
      if (logger.isTraceEnabled()) {
        logger.trace(
            "Notifying {}(s): {}",
            eventType == PluginEvent.EventType.READER_CONNECTED ? "connection" : "disconnection",
            changedReaderNames);
      }
      notifyObservers(new PluginEvent(pluginName, changedReaderNames, eventType));
    }

    /**
     * (private)<br>
     * Compares the list of current readers to the list provided by the system and adds or removes
     * readers accordingly.<br>
     * Observers are notified of changes.
     *
     * @param actualNativeReadersNames the list of readers currently known by the system
     */
    private void processChanges(Set<String> actualNativeReadersNames) throws ReaderIOException {
      SortedSet<String> changedReaderNames = new ConcurrentSkipListSet<String>();
      /*
       * parse the current readers list, notify for disappeared readers, update
       * readers list
       */
      final Collection<Reader> readerCollection = readers.values();
      for (Reader reader : readerCollection) {
        if (!actualNativeReadersNames.contains(reader.getName())) {
          changedReaderNames.add(reader.getName());
        }
      }
      /* notify disconnections if any and update the reader list */
      if (!changedReaderNames.isEmpty()) {
        /* list update */
        for (Reader reader : readerCollection) {
          if (!actualNativeReadersNames.contains(reader.getName())) {
            removeReader(reader);
          }
        }
        notifyChanges(PluginEvent.EventType.READER_DISCONNECTED, changedReaderNames);
        /* clean the list for a possible connection notification */
        changedReaderNames.clear();
      }
      /*
       * parse the new readers list, notify for readers appearance, update readers
       * list
       */
      for (String readerName : actualNativeReadersNames) {
        if (!getReadersNames().contains(readerName)) {
          addReader(readerName);
          /* add to the notification list */
          changedReaderNames.add(readerName);
        }
      }
      /* notify connections if any */
      if (!changedReaderNames.isEmpty()) {
        notifyChanges(PluginEvent.EventType.READER_CONNECTED, changedReaderNames);
      }
    }

    /**
     * Reader monitoring loop<br>
     * Checks reader insertions and removals<br>
     * Notifies observers of any changes
     */
    @Override
    public void run() {
      try {
        while (running) {
          /* retrieves the current readers names list */
          Set<String> actualNativeReadersNames = observablePluginSpi.searchAvailableReadersNames();
          /*
           * checks if it has changed this algorithm favors cases where nothing change
           */
          Set<String> currentlyRegisteredReaderNames = getReadersNames();
          if (!currentlyRegisteredReaderNames.containsAll(actualNativeReadersNames)
              || !actualNativeReadersNames.containsAll(currentlyRegisteredReaderNames)) {
            processChanges(actualNativeReadersNames);
          }
          /* sleep for a while. */
          Thread.sleep(monitoringCycleDuration);
        }
      } catch (InterruptedException e) {
        logger.info(
            "[{}] The observation of this plugin is stopped, possibly because there is no more registered observer.",
            pluginName);
        // Restore interrupted state...
        Thread.currentThread().interrupt();
      } catch (ReaderIOException e) {
        getObservationExceptionHandler().onPluginObservationError(getName(), e);
      }
    }
  }
}