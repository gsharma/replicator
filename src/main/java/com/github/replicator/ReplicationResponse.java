package com.github.replicator;

/**
 * Framework for replication service responses.
 * 
 * @author gaurav
 */
public class ReplicationResponse {
  private long serverTstampMillis;
  private int ackEventsCount;

  public long getServerTstampMillis() {
    return serverTstampMillis;
  }

  public void setServerTstampMillis(long serverTstampMillis) {
    this.serverTstampMillis = serverTstampMillis;
  }

  public int getAckEventsCount() {
    return ackEventsCount;
  }

  public void setAckEventsCount(int ackEventsCount) {
    this.ackEventsCount = ackEventsCount;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("ReplicationResponse [serverTstampMillis=").append(serverTstampMillis)
        .append(", ackEventsCount=").append(ackEventsCount).append("]");
    return builder.toString();
  }
}
