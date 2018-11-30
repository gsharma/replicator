package com.github.replicator;

import java.util.HashMap;
import java.util.Map;

/**
 * Framework for replication service requests.
 * 
 * @author gaurav
 */
public class ReplicationRequest {
  private double requestId;
  private Map<Long, MultiObjectSMRLogEvent> events = new HashMap<>();

  public void setRequestId(double requestId) {
    this.requestId = requestId;
  }

  public double getRequestId() {
    return requestId;
  }

  public void addEvent(final Long checksum, final MultiObjectSMRLogEvent event) {
    events.putIfAbsent(checksum, event);
  }

  public Map<Long, MultiObjectSMRLogEvent> getEvents() {
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
