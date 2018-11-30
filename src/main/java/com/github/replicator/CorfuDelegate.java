package com.github.replicator;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.corfudb.protocols.logprotocol.SMREntry;
import org.corfudb.protocols.logprotocol.LogEntry;
import org.corfudb.protocols.logprotocol.LogEntry.LogEntryType;
import org.corfudb.protocols.logprotocol.MultiObjectSMREntry;
import org.corfudb.protocols.logprotocol.MultiSMREntry;
import org.corfudb.protocols.wireprotocol.DataType;
import org.corfudb.protocols.wireprotocol.ILogData;
import org.corfudb.protocols.wireprotocol.TokenResponse;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.CorfuRuntime.CorfuRuntimeParameters;
import org.corfudb.runtime.view.ObjectsView;
import org.corfudb.runtime.view.StreamOptions;
import org.corfudb.runtime.view.stream.IStreamView;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Metric;
import com.github.replicator.LogEventEntry.MutableOperation;

/**
 * All datastore centric ops are here.
 * 
 * @author gaurav
 */
public final class CorfuDelegate {
  private static final Logger logger = LogManager.getLogger(CorfuDelegate.class.getSimpleName());

  // all Mutators, MutatorAccessors in 2 core collections: ISMRMap (its children) and CorfuTable
  private final static String SMR_METHOD_PUT = "put";
  private final static String SMR_METHOD_PUTALL = "putAll";
  private final static String SMR_METHOD_REMOVE = "remove";
  private final static String SMR_METHOD_CLEAR = "clear";

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

  private final AtomicBoolean running = new AtomicBoolean();
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
  public CorfuDelegate init(final ReplicationServiceConfiguration config) {
    if (!running.get()) {
      this.config = config;
      this.host = config.getCorfuHost();
      this.port = config.getCorfuPort();
      logger.info(String.format("Boostrapping corfu delegate to connect to %s:%d", host, port));
      runtime.parseConfigurationString(host + ":" + port);
      runtime = runtime.connect();
      logger.info(runtime.getStreamsView().getCurrentLayout().toString());
      logger.info(runtime.getLayoutView().getRuntimeLayout().toString());
      boolean connectionStatus = false;
      if (!runtime.isShutdown()) {
        initTransactionStream();
        connectionStatus = true;
        logger.info(String.format("Successfully bootstrapped corfu delegate to connect to %s:%d",
            host, port));
      } else {
        logger.info(
            String.format("Failed to bootstrap corfu delegate to connect to %s:%d", host, port));
      }
      running.set(connectionStatus);
    } else {
      logger.info(String.format(
          "Cannot bootstrap an already running corfu delegate to connect to %s:%d", host, port));
    }
    return this;
  }

  /**
   * Setup support for streaming from transaction_stream.
   */
  private void initTransactionStream() {
    logger.info("Initializing transaction stream");
    final StreamOptions options = new StreamOptions(true);
    final IStreamView transactionStream =
        runtime.getStreamsView().get(ObjectsView.TRANSACTION_STREAM_ID, options);
    transactionStream.seek(0L);
    allStreamViews.put(ObjectsView.TRANSACTION_STREAM_ID, transactionStream);
  }

  /**
   * Save the provided event to a stream with the name corresponding to the component name of the
   * event.
   */
  public void saveEvent(final MultiObjectSMRLogEvent event) throws Exception {
    if (!running.get()) {
      throw new IllegalStateException(
          "Cannot perform datastore operations without a live corfu delegate");
    }
    Objects.requireNonNull(event);
    // logger.info("Saving " + event);
    final UUID streamId = CorfuRuntime.getStreamID(config.getStreamName());
    allStreamViews.putIfAbsent(streamId, runtime.getStreamsView().get(streamId));
    final IStreamView streamView = allStreamViews.get(streamId);
    final long tailOffsetBefore = tailOffset(streamId);
    final byte[] flattenedEvent = MultiObjectSMRLogEvent.jsonSerialize(event);
    // TODO: check for supported types
    streamView.append(flattenedEvent);
    final long tailOffsetAfter = tailOffset(streamId);
    logger.info(String.format("stream:%s, offsets::pre:%d, post:%d, saved %s",
        config.getStreamName(), tailOffsetBefore, tailOffsetAfter, event));
  }

  /**
   * Fetch a batch of events from the last offset position for every stream.
   */
  public List<MultiObjectSMRLogEvent> fetchEvents() {
    if (!running.get()) {
      throw new IllegalStateException(
          "Cannot perform datastore operations without a live corfu delegate");
    }
    // final String streamName = "Transaction_Stream";
    final String streamName = config.getStreamName();
    final List<MultiObjectSMRLogEvent> events = new ArrayList<>();
    final UUID streamId = CorfuRuntime.getStreamID(streamName);
    logger.info(String.format("Streams::[%s:%s][%s:%s]", "Transaction_Stream",
        ObjectsView.TRANSACTION_STREAM_ID.toString(), streamName, streamId.toString()));
    allStreamViews.putIfAbsent(streamId, runtime.getStreamsView().get(streamId));
    final IStreamView streamView = allStreamViews.get(streamId);
    final long tailOffset = tailOffset(streamId);
    if (tailOffset == emptyStreamOffset) {
      logger.info("No events in stream: " + streamName);
      return events;
    }

    //
    // TODO: startOffset should be configurable to allow the replicator to shutdown and resume from
    // either the last streamed offset or a chosen offset of interest
    long startOffset = 0;
    globalStreamOffsets.putIfAbsent(streamName, 0L);
    startOffset = globalStreamOffsets.get(streamName);

    long highWatermark = 0;
    if (tailOffset - startOffset > config.getReplicationStreamDepth()) {
      highWatermark = startOffset + config.getReplicationStreamDepth();
    } else {
      highWatermark = tailOffset;
    }

    logger.info(String.format("Fetching events from stream:%s, offsets::start:%d, end:%d",
        streamName, startOffset, highWatermark));
    final List<ILogData> eventsInLog = streamView.remainingUpTo(highWatermark);

    if (eventsInLog != null) {
      for (final ILogData eventInLog : eventsInLog) {
        if (eventInLog == null) {
          continue;
        }
        final DataType dataType = eventInLog.getType();
        // final LogEntry logEntry = eventInLog.getLogEntry(runtime);
        // logger.info(eventInLog.getLogEntry(runtime));
        if (dataType != DataType.DATA) {
          logger.info(String.format("Skipping %s log event, offset:%d", dataType,
              eventInLog.getGlobalAddress()));
          continue;
        }
        final Set<UUID> streamIds = eventInLog.getStreams();
        if (!allStreamViews.keySet().containsAll(streamIds)) {
          logger.info(String.format(
              "Unknown streams detected for log event:: expected:%s, observed:%s, offset %d",
              allStreamViews.keySet(), streamIds, eventInLog.getGlobalAddress()));
          continue;
        }

        try {
          // It is important to note that in case of any custom types, the ILogData#getLogEntry()
          // will blow up with a ClassCastException. This implies that even though the methodology
          // below for checking the LogEntryType might be the right/clean way to check the log entry
          // type, it does not always work in practice.
          //
          // final LogEntry logEntry = eventInLog.getLogEntry(runtime);
          // final LogEntryType logEntryType = logEntry.getType();
          // logger.info(String.format("Processing %s type log event", logEntryType));

          /**
           * TODO: add handlers for supported types
           * 
           * Payload types:<br/>
           * 
           * 1. NOP(0, LogEntry.class)<br/>
           * 2. SMR(1, SMREntry.class)<br/>
           * 3. MULTIOBJSMR(7, MultiObjectSMREntry.class)<br/>
           * 4. MULTISMR(8, MultiSMREntry.class)<br/>
           * 5. CHECKPOINT(10, CheckpointEntry.class)<br/>
           * 6. Other custom types like byte[]<br/>
           */
          final Long eventLogOffset = eventInLog.getGlobalAddress();
          final Object eventPayload = eventInLog.getPayload(runtime);
          final LogEntry entry = eventInLog.getLogEntry(runtime);
          // TODO: check for handle-able entry types
          final LogEntryType entryType = entry.getType();
          if (eventPayload != null) {
            logger.info(String.format("Processing %s type log event", entryType));
            MultiObjectSMRLogEvent event = null;
            final Class eventPayloadClass = eventPayload.getClass();
            if (SupportedLogEntryType.BYTE_ARRAY.getClazz() == eventPayloadClass) {
              final byte[] byteArrayEntry = (byte[]) eventPayload;
              event = process(eventLogOffset, byteArrayEntry);
            } else if (SupportedLogEntryType.MULTIOBJECT_SMR_ENTRY
                .getClazz() == eventPayloadClass) {
              final MultiObjectSMREntry multiObjectSMREntry = (MultiObjectSMREntry) eventPayload;
              event = process(config, eventLogOffset, multiObjectSMREntry);
            } else {
              logger.warn(String.format("Skipping unsupported %s type log event, offset:%d",
                  eventPayloadClass, eventLogOffset));
            }
            if (event != null) {
              events.add(event);
            }
          } else {
            logger.info(String.format("Skipping null payload %s log event, offset:%d", entryType,
                eventInLog.getGlobalAddress()));
          }
        } catch (Exception serdeIssue) {
          // serDe is almost always a bitch, don't just stall the pipeline when shit happens, plan
          // for repair operations
          logger.error("Deserialization issue encountered for an event", serdeIssue);
        }
      }
    }
    logger.info(String.format("Refreshing cached offsets:: stream:%s, offset:%d", streamName,
        highWatermark));
    globalStreamOffsets.put(streamName, highWatermark);
    return events;
  }

  // handler for byte[] events
  private static MultiObjectSMRLogEvent process(final Long offset, final byte[] eventPayload)
      throws Exception {
    final MultiObjectSMRLogEvent event = MultiObjectSMRLogEvent.jsonDeserialize(eventPayload);
    return event;
  }

  /**
   * Handler for MultiObjectSMREntry events - handle all Mutators and MutatorAccessors in the 2 core
   * collections: ISMRMap (its children) and CorfuTable.
   */
  private static MultiObjectSMRLogEvent process(final ReplicationServiceConfiguration config,
      final Long offset, final MultiObjectSMREntry eventPayload) throws Exception {
    final MultiObjectSMRLogEvent event = new MultiObjectSMRLogEvent();
    event.setStreamName(config.getStreamName());
    event.setOffset(offset);
    event.setType(LogEventType.MULTIOBJECTSMR);
    for (final Map.Entry<UUID, MultiSMREntry> payloadEntry : eventPayload.getEntryMap()
        .entrySet()) {
      // final UUID entryStreamId = payloadEntry.getKey();
      final StringBuilder builder = new StringBuilder();
      for (final SMREntry operation : payloadEntry.getValue().getUpdates()) {
        final LogEventEntry logEntry = new LogEventEntry();
        final String opMethod = operation.getSMRMethod();
        logEntry.setOperation(MutableOperation.fromMethod(opMethod));

        // for SMR_METHOD_CLEAR, both key and value will be null
        final Object key =
            operation.getSMRArguments().length > 0 ? operation.getSMRArguments()[0] : null;
        final Class keyClass = key != null ? key.getClass() : null;

        // for SMR_METHOD_REMOVE, value will be null
        final Object value =
            operation.getSMRArguments().length > 1 ? operation.getSMRArguments()[1] : null;
        final Class valueClass = value != null ? value.getClass() : null;

        builder.append("\n\t")
            .append(String.format(
                "[MultiObjectSMREntry::[Offset:%d] [Op:%s] [K:[%s][%s]] [V:[%s][%s]]]", offset,
                opMethod, key, keyClass, value, valueClass));
        switch (opMethod) {
          // TODO: finish me
          case SMR_METHOD_PUT:
            logEntry.setKey(key);
            logEntry.setKeyClass(keyClass);
            logEntry.setValue(value);
            logEntry.setValueClass(valueClass);
            break;
          case SMR_METHOD_REMOVE:
            logEntry.setKey(key);
            logEntry.setKeyClass(keyClass);
            break;
          case SMR_METHOD_CLEAR:
            break;
          case SMR_METHOD_PUTALL:
            logEntry.setKey(key);
            logEntry.setKeyClass(keyClass);
            break;
          default:
            logger.error(String.format(
                "Unhandled [MultiObjectSMREntry::[Offset:%d] [Op:%s] [K:[%s][%s]] [V:[%s][%s]]]",
                offset, opMethod, key, keyClass, value, valueClass));
            continue;
        }
        event.addEntry(logEntry);
      }
      logger.info(String.format("Processed MultiObjectSMREntry log event, offset:%d %s", offset,
          builder.toString()));
      // logger.info(String.format("Processed %s", event));
    }
    return event;
  }

  /**
   * Save a batch of events.
   */
  public void saveEvents(final Collection<MultiObjectSMRLogEvent> events) throws Exception {
    if (!running.get()) {
      throw new IllegalStateException(
          "Cannot perform datastore operations without a live corfu delegate");
    }
    Objects.requireNonNull(events);
    for (MultiObjectSMRLogEvent event : events) {
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

  // leak for testing
  CorfuRuntime getRuntime() {
    return runtime;
  }

  public CorfuDelegate tini() {
    if (running.get()) {
      boolean shutdownStatus = false;
      logger.info(String.format("Shutting down corfu delegate connected to %s:%d", host, port));
      logger.info(runtime.getStreamsView().getCurrentLayout().toString());
      logger.info(runtime.getLayoutView().getRuntimeLayout().toString());
      logCorfuCounters();
      runtime.shutdown();
      if (runtime.isShutdown()) {
        shutdownStatus = true;
        logger.info(
            String.format("Successfully shutdown corfu delegate connected to %s:%d", host, port));
      } else {
        logger.info(
            String.format("Failed to shutdown corfu delegate connected to %s:%d", host, port));
      }
      running.compareAndSet(true, shutdownStatus);
    } else {
      logger.info(String.format(
          "Cannot shutdown an already stopped corfu delegate connected to %s:%d", host, port));
    }
    return this;
  }

  public boolean isRunning() {
    return running.get();
  }

  private void logCorfuCounters() {
    final Map<String, Metric> corfuMetrics = runtime.getMetrics().getMetrics();
    if (corfuMetrics != null && !corfuMetrics.isEmpty()) {
      final StringBuilder builder = new StringBuilder();
      for (final Map.Entry<String, Metric> corfuMetric : corfuMetrics.entrySet()) {
        if (corfuMetric != null) {
          // at this time, let's only worry about handling Counter metrics
          if (corfuMetric.getValue() != null
              && corfuMetric.getValue().getClass().isAssignableFrom(Counter.class)) {
            builder.append('[').append(corfuMetric.getKey()).append(':')
                .append(((Counter) corfuMetric.getValue()).getCount()).append(']');
          }
        }
      }
      logger.info(String.format("Corfu metrics::%s", builder.toString()));
    }
  }

  // Supported LogEntry types
  private enum SupportedLogEntryType {
    BYTE_ARRAY(new byte[0].getClass()), SMR_ENTRY(SMREntry.class), MULTIOBJECT_SMR_ENTRY(
        MultiObjectSMREntry.class), MULTI_SMR_ENTRY(MultiSMREntry.class);

    public Class<?> getClazz() {
      return clazz;
    }

    private Class<?> clazz;

    private SupportedLogEntryType(final Class<?> clazz) {
      this.clazz = clazz;
    }
  }

}
