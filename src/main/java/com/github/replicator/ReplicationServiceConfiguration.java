package com.github.replicator;

/**
 * An immutable config holder for the replication service to bootstrap.
 * 
 * TODO: provide a way to populate via properties, as well
 * 
 * @author gaurav
 */
public final class ReplicationServiceConfiguration {
  // receiver or sender service config
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

  // streamer config
  private final long streamStartOffset;
  private final long replicationIntervalSeconds;
  private final int replicationStreamDepth;

  // remote service config
  private final String remoteServiceUrl;

  // TODO: switch this fugly ctor to a builder/fluent style
  public ReplicationServiceConfiguration(final String serverHost, final int serverPort,
      final int serverThreadCount, final int workerThreadCount, final int readerIdleTimeSeconds,
      final int writerIdleTimeSeconds, final int compressionLevel, final ReplicationMode mode,
      final String corfuHost, final int corfuPort, final long replicationIntervalSeconds,
      final long streamStartOffset, final int replicationStreamDepth,
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

    this.streamStartOffset = streamStartOffset;
    this.replicationIntervalSeconds = replicationIntervalSeconds;
    this.replicationStreamDepth = replicationStreamDepth;

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

  public long getStreamStartOffset() {
    return streamStartOffset;
  }

  public long getReplicationIntervalSeconds() {
    return replicationIntervalSeconds;
  }

  public int getReplicationStreamDepth() {
    return replicationStreamDepth;
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
        .append(corfuHost).append(", corfuPort=").append(corfuPort).append(", streamStartOffset=")
        .append(streamStartOffset).append(", replicationIntervalSecs=")
        .append(replicationIntervalSeconds).append(", replicationStreamDepth=")
        .append(replicationStreamDepth).append(", remoteServiceUrl=").append(remoteServiceUrl)
        .append("]");
    return builder.toString();
  }

}
