package com.github.replicator;

import java.util.List;

/**
 * Framework for replication service requests.
 * 
 * @author gaurav
 */
public class ReplicationRequest {
  private double requestId;
  private List<LogEvent> events;

  public void setRequestId(double requestId) {
    this.requestId = requestId;
  }

  public double getRequestId() {
    return requestId;
  }

  public void setEvents(List<LogEvent> events) {
    this.events = events;
  }

  public List<LogEvent> getEvents() {
    return events;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("ReplicationRequest [requestId=").append(requestId).append(", events=")
        .append(events).append("]");
    return builder.toString();
  }

}
