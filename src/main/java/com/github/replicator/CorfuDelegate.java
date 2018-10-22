package com.github.replicator;

import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicLong;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import org.corfudb.protocols.wireprotocol.TokenResponse;
import org.corfudb.runtime.CorfuRuntime;
import org.corfudb.runtime.CorfuRuntime.CorfuRuntimeParameters;
import org.corfudb.runtime.view.stream.IStreamView;

/**
 * All datastore centric ops here.
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

  private final ConcurrentMap<String, Long> globalStreamOffsets =
      new ConcurrentHashMap<String, Long>();
  private final AtomicLong statusOffset = new AtomicLong(-6L);
  private final ConcurrentMap<UUID, IStreamView> allStreamViews = new ConcurrentHashMap<>();

  /**
   * Initialize the delegate
   */
  public boolean init(String host, int port) {
    logger.info(String.format("Boostrapping corfu delegate to connect to %s:%d", host, port));
    runtime.parseConfigurationString(host + ":" + port);
    runtime = runtime.connect();
    boolean connectionStatus = false;
    if (!runtime.isShutdown()) {
      connectionStatus = true;
      logger.info("Successfully bootstrapped corfu delegate");
    } else {
      logger.info("Failed to bootstrap corfu delegate");
    }
    return connectionStatus;
  }

  public void reapStream() {
    
  }

  private long tailOffset(final UUID streamId) {
    TokenResponse response =
        runtime.getSequencerView().nextToken(Collections.singleton(streamId), 0);
    long tailOffset = response.getTokenValue();
    return tailOffset;
  }

  public boolean tini() {
    logger.info("Shutting down corfu delegate");
    runtime.shutdown();
    boolean shutdownStatus = false;
    if (runtime.isShutdown()) {
      shutdownStatus = true;
      logger.info("Successfully shutdown corfu delegate");
    } else {
      logger.info("Failed to shutdown corfu delegate");
    }
    return shutdownStatus;
  }

}
