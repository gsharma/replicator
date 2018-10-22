package com.github.replicator;

/**
 * Framework for replication service responses.
 * 
 * @author gaurav
 */
public class ReplicationResponse {
  private long serverTstampMillis;

  public long getServerTstampMillis() {
    return serverTstampMillis;
  }

  public void setServerTstampMillis(long serverTstampMillis) {
    this.serverTstampMillis = serverTstampMillis;
  }

  @Override
  public String toString() {
    return "Replication Response [serverTstampMillis=" + serverTstampMillis + "]";
  }
}
