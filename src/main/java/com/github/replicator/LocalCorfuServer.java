package com.github.replicator;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

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
  private Thread runner;

  private ServerContext context;
  private NettyServerRouter router;
  private volatile boolean running;

  private final String host;
  private final int port;

  public LocalCorfuServer(final String host, final int port) {
    this.host = host;
    this.port = port;
  }

  public void init() throws Exception {
    final Map<String, Object> serverOptions = defaultServerOptions();
    runner = new Thread() {
      {
        setName("Local Corfu Server");
      }

      @Override
      public void run() {
        serverLoop(serverOptions);
      }
    };
    runner.start();
    // spin like silly waiting for server to bootstrap
    long waitMillis = 200L;
    while (!running) {
      logger.info(String.format("Waiting %d millis for corfu server to bootstrap on %s:%d",
          waitMillis, host, port));
      Thread.sleep(waitMillis);
    }
    logger.info(
        String.format("Successfully fired up corfu server with options::\n%s", serverOptions));
  }

  public void tini() {
    logger.info(String.format("Shutting down corfu server on %s:%d", host, port));
    running = false;
    runner.interrupt();
    CorfuServer.cleanShutdown(router);
    context.close();
    logger.info(String.format("Successfully shutdown corfu server on %s:%d", host, port));
  }

  public boolean isRunning() {
    return running;
  }

  private void serverLoop(final Map<String, Object> options) {
    String address = (String) options.get("--address");
    int port = Integer.parseInt((String) options.get("<port>"));
    context = new ServerContext(options);
    BaseServer baseServer = new BaseServer(context);
    SequencerServer sequencerServer = new SequencerServer(context);
    LayoutServer layoutServer = new LayoutServer(context);
    LogUnitServer logUnitServer = new LogUnitServer(context);
    ManagementServer managementServer = new ManagementServer(context);
    router = new NettyServerRouter(
        Arrays.asList(baseServer, sequencerServer, layoutServer, logUnitServer, managementServer));
    context.setServerRouter(router);
    try {
      running = true;
      CorfuServer.startAndListen(context.getBossGroup(), context.getWorkerGroup(),
          (bs) -> CorfuServer.configureBootstrapOptions(context, bs), context, router, address,
          port).channel().closeFuture().sync().get();
    } catch (InterruptedException interrupted) {
    } catch (Exception problem) {
      throw new RuntimeException("Error while running server", problem);
    } finally {
      if (running) {
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
