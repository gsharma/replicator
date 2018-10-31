package com.github.replicator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.corfudb.infrastructure.BaseServer;
import org.corfudb.infrastructure.CorfuServer;
import org.corfudb.infrastructure.LayoutServer;
import org.corfudb.infrastructure.LogUnitServer;
import org.corfudb.infrastructure.ManagementServer;
import org.corfudb.infrastructure.NettyServerRouter;
import org.corfudb.infrastructure.SequencerServer;
import org.corfudb.infrastructure.ServerContext;

/**
 * Embedded corfu server useful for testing both ends of the async replication pipeline.
 * 
 * @author gsharma
 */
public final class LocalCorfuServer {
  private static final Logger logger = LogManager.getLogger(LocalCorfuServer.class.getSimpleName());
  private Thread serverWorker;

  private final AtomicBoolean running = new AtomicBoolean(false);
  private ServerContext serverContext;
  private NettyServerRouter nettyServerRouter;

  private final String host;
  private final int port;

  public LocalCorfuServer(final String host, final int port) {
    this.host = host;
    this.port = port;
  }

  public void init() throws Exception {
    final Map<String, Object> serverOptions = defaultServerOptions();
    serverWorker = new Thread() {
      {
        setName("Local Corfu Server");
      }

      @Override
      public void run() {
        serverLoop(serverOptions);
      }
    };
    serverWorker.start();
    // spin like silly waiting for server to bootstrap
    long waitMillis = 200L;
    while (!running.get()) {
      logger.info(String.format("Waiting %d millis for corfu server to bootstrap on %s:%d",
          waitMillis, host, port));
      Thread.sleep(waitMillis);
    }
    logger.info(
        String.format("Successfully fired up corfu server with options::\n%s", serverOptions));
  }

  public void tini() {
    logger.info(String.format("Shutting down corfu server on %s:%d", host, port));
    running.set(false);
    serverWorker.interrupt();
    CorfuServer.cleanShutdown(nettyServerRouter);
    serverContext.close();
    logger.info(String.format("Successfully shutdown corfu server on %s:%d", host, port));
  }

  public boolean isRunning() {
    return running.get();
  }

  private void serverLoop(final Map<String, Object> options) {
    final String address = (String) options.get("--address");
    final int port = Integer.parseInt((String) options.get("<port>"));
    serverContext = new ServerContext(options);
    final BaseServer baseServer = new BaseServer(serverContext);
    final SequencerServer sequencerServer = new SequencerServer(serverContext);
    final LayoutServer layoutServer = new LayoutServer(serverContext);
    final LogUnitServer logUnitServer = new LogUnitServer(serverContext);
    final ManagementServer managementServer = new ManagementServer(serverContext);
    nettyServerRouter = new NettyServerRouter(
        Arrays.asList(baseServer, sequencerServer, layoutServer, logUnitServer, managementServer));
    serverContext.setServerRouter(nettyServerRouter);
    try {
      running.set(true);
      CorfuServer.startAndListen(serverContext.getBossGroup(), serverContext.getWorkerGroup(),
          (bootstrap) -> CorfuServer.configureBootstrapOptions(serverContext, bootstrap),
          serverContext, nettyServerRouter, address, port).channel().closeFuture().sync().get();
    } catch (InterruptedException interrupted) {
    } catch (Exception problem) {
      throw new RuntimeException("Error while running server", problem);
    } finally {
      if (running.get()) {
        logger.info("Shutting down local corfu server loop");
        tini();
      }
    }
  }

  private Map<String, Object> defaultServerOptions() {
    final Map<String, Object> options = new HashMap<>();
    options.put("--log-path", null);
    options.put("--single", true);
    options.put("--cluster-id", "auto");
    options.put("--Threads", "0");
    options.put("--Prefix", "");
    options.put("--address", host);
    options.put("<port>", Integer.toString(port));
    options.put("--network-interface", null);
    options.put("--implementation", "nio");
    options.put("--memory", true);
    options.put("--cache-heap-ratio", "0.5");
    options.put("--HandshakeTimeout", "10");
    options.put("--initial-token", "-1");
    options.put("--sequencer-cache-size", "250000");
    options.put("--batch-size", "100");
    options.put("--compact", "600");
    options.put("--log-level", "INFO");
    options.put("--management-server", null);
    options.put("--no-verify", false);
    options.put("--enable-tls", false);
    options.put("--enable-sasl-plain-text-auth", false);
    options.put("--sasl-plain-text-username-file", null);
    options.put("--sasl-plain-text-password-file", null);
    options.put("--agent", false);
    return options;
  }
}
