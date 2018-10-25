package com.github.replicator;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.corfudb.protocols.wireprotocol.DataType;
import org.corfudb.protocols.wireprotocol.ILogData;
import org.corfudb.protocols.wireprotocol.TokenResponse;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.CorfuRuntime.CorfuRuntimeParameters;
import org.corfudb.runtime.view.stream.IStreamView;

/**
 * All datastore centric ops are here.
 * 
 * @author gaurav
 */
public final class CorfuDelegate {
  private static final Logger logger = LogManager.getLogger(CorfuDelegate.class.getSimpleName());

  /**
   * Default parameters: undoDisabled=false, optimisticUndoDisabled=false, maxWriteSize=0,
   * useFastLoader=false, bulkReadSize=10, fastLoaderTimeout=PT30M, holeFillRetry=10,
   * cacheDisabled=false, numCacheEntries=5000, cacheExpiryTime=9223372036854775807,
   * handshakeTimeout=10, backpointersDisabled=false, holeFillingDisabled=false, writeRetry=5,
   * trimRetry=2, tlsEnabled=false, keyStore=null, ksPasswordFile=null, trustStore=null,
   * tsPasswordFile=null, saslPlainTextEnabled=false, usernameFile=null, passwordFile=null,
   * requestTimeout=PT5S, connectionTimeout=PT0.5S, connectionRetryRate=PT1S,
   * clientId=31fafb80-40ec-4817-9e5a-360c2767765e, clusterId=null, socketType=NIO,
   * systemDownHandlerTriggerLimit=20, layoutServers=[], nettyEventLoop=null,
   * nettyEventLoopThreadFormat=netty-%d, nettyEventLoopThreads=0, shutdownNettyEventLoop=true,
   * customNettyChannelOptions={}, uncaughtExceptionHandler=null, invalidateRetry=5
   */
  private final CorfuRuntimeParameters parameters = CorfuRuntimeParameters.builder().build();
  private CorfuRuntime runtime =
      CorfuRuntime.fromParameters(parameters).setTransactionLogging(true);

  private ReplicationServiceConfiguration config;
  private String host;
  private int port;
  private final long emptyStreamOffset = -6L;
  // we should push the last set of already streamed offsets to disk
  private final ConcurrentMap<String, Long> globalStreamOffsets =
      new ConcurrentHashMap<String, Long>();
  private final ConcurrentMap<UUID, IStreamView> allStreamViews = new ConcurrentHashMap<>();

  /**
   * Initialize the delegate
   */
  public boolean init(final ReplicationServiceConfiguration config) {
    logger.info(String.format("Boostrapping corfu delegate to connect to %s:%d", host, port));
    this.config = config;
    this.host = config.getCorfuHost();
    this.port = config.getCorfuPort();
    runtime.parseConfigurationString(host + ":" + port);
    runtime = runtime.connect();
    logger.info(runtime.getStreamsView().getCurrentLayout().toString());
    logger.info(runtime.getLayoutView().getRuntimeLayout().toString());
    boolean connectionStatus = false;
    if (!runtime.isShutdown()) {
      connectionStatus = true;
      logger.info(String.format("Successfully bootstrapped corfu delegate to connect to %s:%d",
          host, port));
    } else {
      logger.info(
          String.format("Failed to bootstrap corfu delegate to connect to %s:%d", host, port));
    }
    return connectionStatus;
  }

  /**
   * Save the provided event to a stream with the name corresponding to the component name of the
   * event.
   */
  public void saveEvent(final LogEvent event) throws Exception {
    Objects.requireNonNull(event);
    // logger.info("Saving " + event);
    final UUID streamId = CorfuRuntime.getStreamID(LogEvent.STREAM_NAME);
    allStreamViews.putIfAbsent(streamId, runtime.getStreamsView().get(streamId));
    final IStreamView streamView = allStreamViews.get(streamId);
    final long tailOffsetBefore = tailOffset(streamId);
    final byte[] flattenedEvent = LogEvent.jsonSerialize(event);
    runtime.getObjectsView().TXBegin();
    try {
      streamView.append(flattenedEvent);
    } finally {
      runtime.getObjectsView().TXEnd();
    }
    // streamView.append(LogEvent.serialize(event));
    final long tailOffsetAfter = tailOffset(streamId);
    logger.info(String.format("stream:%s, offsets::pre:%d, post:%d, saved %s", LogEvent.STREAM_NAME,
        tailOffsetBefore, tailOffsetAfter, event));
  }

  /**
   * Fetch a batch of events from the last offset position for every stream.
   */
  public List<LogEvent> fetchEvents() {
    final List<LogEvent> events = new ArrayList<>();
    final UUID streamId = CorfuRuntime.getStreamID(LogEvent.STREAM_NAME);
    allStreamViews.putIfAbsent(streamId, runtime.getStreamsView().get(streamId));
    final IStreamView streamView = allStreamViews.get(streamId);
    final long tailOffset = tailOffset(streamId);
    if (tailOffset == emptyStreamOffset) {
      logger.info("No events in stream: " + LogEvent.STREAM_NAME);
      return events;
    }

    //
    // TODO: startOffset should be configurable to allow the replicator to shutdown and resume from
    // either the last streamed offset or a chosen offset of interest
    long startOffset = 0;
    globalStreamOffsets.putIfAbsent(LogEvent.STREAM_NAME, 0L);
    startOffset = globalStreamOffsets.get(LogEvent.STREAM_NAME);

    long highWatermark = 0;
    if (tailOffset - startOffset > config.getReplicationStreamDepth()) {
      highWatermark = startOffset + config.getReplicationStreamDepth();
    } else {
      highWatermark = tailOffset;
    }

    logger.info(String.format("Fetching events from stream:%s, offsets::start:%d, end:%d",
        LogEvent.STREAM_NAME, startOffset, highWatermark));
    List<ILogData> eventsInLog = null;
    runtime.getObjectsView().TXBegin();
    try {
      eventsInLog = streamView.remainingUpTo(highWatermark);
    } finally {
      runtime.getObjectsView().TXEnd();
    }
    if (eventsInLog != null) {
      for (final ILogData eventInLog : eventsInLog) {
        final DataType type = eventInLog.getType();
        if (type != DataType.DATA) {
          logger.info(String.format("Skipping %s log event", type));
          continue;
        }
        try {
          final byte[] serializedEvent = (byte[]) eventInLog.getPayload(runtime);
          final LogEvent readEvent = LogEvent.jsonDeserialize(serializedEvent);
          // final LogEvent readEvent = LogEvent.deserialize(serializedEvent);
          events.add(readEvent);
        } catch (Exception serdeIssue) {
          // serDe is almost always a bitch, don't just stall the pipeline when shit happens, plan
          // for repair operations
          logger.error("Deserialization issue encountered for an event", serdeIssue);
        }
      }
    }
    globalStreamOffsets.put(LogEvent.STREAM_NAME, highWatermark);
    return events;
  }

  /**
   * Save a batch of events.
   */
  public void saveEvents(final List<LogEvent> events) throws Exception {
    Objects.requireNonNull(events);
    for (LogEvent event : events) {
      saveEvent(event);
    }
    logger.info(String.format("Successfully saved %d events", events.size()));
  }

  private long tailOffset(final UUID streamId) {
    final TokenResponse response = runtime.getSequencerView().query(streamId);
    if (logger.isDebugEnabled()) {
      logger.debug(response);
    }
    return response.getTokenValue();
  }

  public boolean tini() {
    boolean shutdownStatus = false;
    logger.info(String.format("Shutting down corfu delegate connected to %s:%d", host, port));
    logger.info(runtime.getStreamsView().getCurrentLayout().toString());
    logger.info(runtime.getLayoutView().getRuntimeLayout().toString());
    runtime.shutdown();
    if (runtime.isShutdown()) {
      shutdownStatus = true;
      logger.info(
          String.format("Successfully shutdown corfu delegate connected to %s:%d", host, port));
    } else {
      logger
          .info(String.format("Failed to shutdown corfu delegate connected to %s:%d", host, port));
    }
    return shutdownStatus;
  }

}
