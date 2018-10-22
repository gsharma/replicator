package com.github.replicator;

/**
 * An immutable config holder for the replication service to bootstrap.
 * 
 * TODO: provide a way to populate via properties, as well
 * 
 * @author gaurav
 */
public final class ReplicationServiceConfiguration {
  private final int serverPort;
  private final int serverThreadCount;
  private final int workerThreadCount;
  private final int readerIdleTimeSeconds;
  private final int writerIdleTimeSeconds;
  private final int compressionLevel;

  private final ReplicationMode mode;

  // local corfu config
  private final String corfuHost;
  private final int corfuPort;

  public ReplicationServiceConfiguration(final int serverPort, final int serverThreadCount,
      final int workerThreadCount, final int readerIdleTimeSeconds, final int writerIdleTimeSeconds,
      final int compressionLevel, final ReplicationMode mode, final String corfuHost,
      final int corfuPort) {
    this.serverPort = serverPort;
    this.serverThreadCount = serverThreadCount;
    this.workerThreadCount = workerThreadCount;
    this.readerIdleTimeSeconds = readerIdleTimeSeconds;
    this.writerIdleTimeSeconds = writerIdleTimeSeconds;
    this.compressionLevel = compressionLevel;
    this.mode = mode;
    this.corfuHost = corfuHost;
    this.corfuPort = corfuPort;
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

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("Configuration [mode=").append(mode).append(", serverPort=").append(serverPort)
        .append(", serverThreadCount=").append(serverThreadCount).append(", workerThreadCount=")
        .append(workerThreadCount).append(", readerIdleTimeSeconds=").append(readerIdleTimeSeconds)
        .append(", writerIdleTimeSeconds=").append(writerIdleTimeSeconds)
        .append(", compressionLevel=").append(compressionLevel).append(", corfuHost=")
        .append(corfuHost).append(", corfuPort=").append(corfuPort).append("]");
    return builder.toString();
  }

}
