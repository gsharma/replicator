package com.github.replicator;

/**
 * An immutable config holder for the replication service to bootstrap.
 * 
 * TODO: provide a way to populate via properties, as well
 * 
 * @author gaurav
 */
public final class ReplicationServiceConfiguration {
  // receive service config
  private final String serverHost;
  private final int serverPort;
  private final int serverThreadCount;
  private final int workerThreadCount;
  private final int readerIdleTimeSeconds;
  private final int writerIdleTimeSeconds;
  private final int compressionLevel;

  // overall replicator mode
  private final ReplicationMode mode;

  // local corfu config
  private final String corfuHost;
  private final int corfuPort;

  // remote service config
  private final long replicationIntervalSeconds;
  private final String remoteServiceUrl;

  public ReplicationServiceConfiguration(final String serverHost, final int serverPort,
      final int serverThreadCount, final int workerThreadCount, final int readerIdleTimeSeconds,
      final int writerIdleTimeSeconds, final int compressionLevel, final ReplicationMode mode,
      final String corfuHost, final int corfuPort, final long replicationIntervalSeconds,
      final String remoteServiceUrl) {
    this.serverHost = serverHost;
    this.serverPort = serverPort;
    this.serverThreadCount = serverThreadCount;
    this.workerThreadCount = workerThreadCount;
    this.readerIdleTimeSeconds = readerIdleTimeSeconds;
    this.writerIdleTimeSeconds = writerIdleTimeSeconds;
    this.compressionLevel = compressionLevel;
    this.mode = mode;
    this.corfuHost = corfuHost;
    this.corfuPort = corfuPort;
    this.replicationIntervalSeconds = replicationIntervalSeconds;
    this.remoteServiceUrl = remoteServiceUrl;
  }

  String getServerHost() {
    return serverHost;
  }

  int getServerPort() {
    return serverPort;
  }

  int getServerThreadCount() {
    return serverThreadCount;
  }

  int getWorkerThreadCount() {
    return workerThreadCount;
  }

  int getReaderIdleTimeSeconds() {
    return readerIdleTimeSeconds;
  }

  int getWriterIdleTimeSeconds() {
    return writerIdleTimeSeconds;
  }

  int getCompressionLevel() {
    return compressionLevel;
  }

  public String getCorfuHost() {
    return corfuHost;
  }

  public int getCorfuPort() {
    return corfuPort;
  }

  public ReplicationMode getMode() {
    return mode;
  }

  public long getReplicationIntervalSeconds() {
    return replicationIntervalSeconds;
  }

  public String getRemoteServiceUrl() {
    return remoteServiceUrl;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Configuration [mode=").append(mode).append(", serverHost=").append(serverHost)
        .append(", serverPort=").append(serverPort).append(", serverThreadCount=")
        .append(serverThreadCount).append(", workerThreadCount=").append(workerThreadCount)
        .append(", readerIdleTimeSeconds=").append(readerIdleTimeSeconds)
        .append(", writerIdleTimeSeconds=").append(writerIdleTimeSeconds)
        .append(", compressionLevel=").append(compressionLevel).append(", corfuHost=")
        .append(corfuHost).append(", corfuPort=").append(corfuPort)
        .append(", replicationIntervalSecs=").append(replicationIntervalSeconds)
        .append(", remoteServiceUrl=").append(remoteServiceUrl).append("]");
    return builder.toString();
  }

}
