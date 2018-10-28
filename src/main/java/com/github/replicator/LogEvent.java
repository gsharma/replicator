package com.github.replicator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;

import org.corfudb.annotations.Accessor;
import org.corfudb.annotations.CorfuObject;
import org.corfudb.annotations.Mutator;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Models an event which is interesting from the purposes of replication.
 * 
 * General CorfuObject guidelines to keep in mind:<br>
 * 1. Do not access a Corfu object's inner state directly.<br>
 * 2. Corfu object classes must be top-level.<br>
 * 3. Corfu object constructors must be without parameters.<br>
 *
 * @author gaurav
 */
@CorfuObject
public class LogEvent implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final ObjectMapper objectMapper = new ObjectMapper();
  public static final String STREAM_NAME = "ReplicationStream";
  private long clientTstamp;
  private Component component;
  private Status status;

  public enum Component {
    HTTP_SERVER, LOAD_BALANCER, REVERSE_PROXY, DATABASE;
  }

  public enum Status {
    UP, DOWN;
  }

  @Accessor
  public long getClientTstamp() {
    return clientTstamp;
  }

  @Mutator(name = "setClientTstamp")
  public void setClientTstamp(long clientTstamp) {
    this.clientTstamp = clientTstamp;
  }

  @Accessor
  public Component getComponent() {
    return component;
  }

  @Mutator(name = "setComponent")
  public void setComponent(Component component) {
    this.component = component;
  }

  @Accessor
  public Status getStatus() {
    return status;
  }

  @Mutator(name = "setStatus")
  public void setStatus(Status status) {
    this.status = status;
  }

  // TODO: make choice of SerDe configurable
  // JSON SerDe
  public static byte[] jsonSerialize(final LogEvent event) throws Exception {
    return objectMapper.writeValueAsBytes(event);
  }

  public static LogEvent jsonDeserialize(final byte[] event) throws Exception {
    return objectMapper.readValue(event, LogEvent.class);
  }

  // Java SerDe
  public static byte[] serialize(final LogEvent event) throws IOException {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    new ObjectOutputStream(byteStream).writeObject(event);
    return byteStream.toByteArray();
  }

  public static LogEvent deserialize(final byte[] event)
      throws IOException, ClassNotFoundException {
    ByteArrayInputStream byteStream = new ByteArrayInputStream(event);
    return (LogEvent) new ObjectInputStream(byteStream).readObject();
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("LogEvent [clientTstamp=").append(clientTstamp).append(", component=")
        .append(component).append(", status=").append(status).append("]");
    return builder.toString();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + (int) (clientTstamp ^ (clientTstamp >>> 32));
    result = prime * result + ((component == null) ? 0 : component.hashCode());
    result = prime * result + ((status == null) ? 0 : status.hashCode());
    return result;
  }

  @Override
  public boolean equals(Object obj) {
    if (this == obj) {
      return true;
    }
    if (obj == null) {
      return false;
    }
    if (!(obj instanceof LogEvent)) {
      return false;
    }
    LogEvent other = (LogEvent) obj;
    if (clientTstamp != other.clientTstamp) {
      return false;
    }
    if (component != other.component) {
      return false;
    }
    if (status != other.status) {
      return false;
    }
    return true;
  }

}
