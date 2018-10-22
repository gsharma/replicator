package com.github.replicator;

/**
 * Framework for replication service requests.
 * 
 * @author gaurav
 */
public class ReplicationRequest {
  private long clientTstampMillis;
  private double requestId;

  public long getClientTstampMillis() {
    return clientTstampMillis;
  }

  public void setClientTstampMillis(long clientTstampMillis) {
    this.clientTstampMillis = clientTstampMillis;
  }

  public void setRequestId(double requestId) {
    this.requestId = requestId;
  }

  public double getRequestId() {
    return requestId;
  }

  @Override
  public String toString() {
    return "Replication Request [clientTstampMillis=" + clientTstampMillis + ", requestId="
        + requestId + "]";
  }

}
