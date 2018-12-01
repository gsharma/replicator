package com.github.replicator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.List;

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
public class MultiObjectSMRLogEvent implements Serializable {
  private static final long serialVersionUID = 1L;
  private static final ObjectMapper objectMapper = new ObjectMapper();
  private String streamName;
  private Long offset;
  private LogEventType type;
  private List<LogEventEntry> entries = new ArrayList<>();

  @Accessor
  public String getStreamName() {
    return streamName;
  }

  @Mutator(name = "setStreamName")
  public void setStreamName(final String streamName) {
    this.streamName = streamName;
  }

  @Accessor
  public Long getOffset() {
    return offset;
  }

  @Mutator(name = "setOffset")
  public void setOffset(final Long offset) {
    this.offset = offset;
  }

  @Accessor
  public LogEventType getType() {
    return type;
  }

  @Mutator(name = "setType")
  public void setType(final LogEventType type) {
    this.type = type;
  }

  @Accessor
  public List<LogEventEntry> getEntries() {
    return entries;
  }

  @Mutator(name = "addEntry")
  public void addEntry(final LogEventEntry entry) {
    this.entries.add(entry);
  }

  // TODO: make choice of SerDe configurable
  // JSON SerDe
  public static byte[] jsonSerialize(final MultiObjectSMRLogEvent event) throws Exception {
    return objectMapper.writeValueAsBytes(event);
  }

  public static MultiObjectSMRLogEvent jsonDeserialize(final byte[] event) throws Exception {
    return objectMapper.readValue(event, MultiObjectSMRLogEvent.class);
  }

  // Java SerDe
  public static byte[] serialize(final MultiObjectSMRLogEvent event) throws IOException {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    new ObjectOutputStream(byteStream).writeObject(event);
    return byteStream.toByteArray();
  }

  public static MultiObjectSMRLogEvent deserialize(final byte[] event)
      throws IOException, ClassNotFoundException {
    ByteArrayInputStream byteStream = new ByteArrayInputStream(event);
    return (MultiObjectSMRLogEvent) new ObjectInputStream(byteStream).readObject();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((entries == null) ? 0 : entries.hashCode());
    result = prime * result + ((offset == null) ? 0 : offset.hashCode());
    result = prime * result + ((streamName == null) ? 0 : streamName.hashCode());
    result = prime * result + ((type == null) ? 0 : type.hashCode());
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
    if (!(obj instanceof MultiObjectSMRLogEvent)) {
      return false;
    }
    MultiObjectSMRLogEvent other = (MultiObjectSMRLogEvent) obj;
    if (entries == null) {
      if (other.entries != null) {
        return false;
      }
    } else if (!entries.equals(other.entries)) {
      return false;
    }
    if (offset == null) {
      if (other.offset != null) {
        return false;
      }
    } else if (!offset.equals(other.offset)) {
      return false;
    }
    if (streamName == null) {
      if (other.streamName != null) {
        return false;
      }
    } else if (!streamName.equals(other.streamName)) {
      return false;
    }
    if (type != other.type) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("MultiObjectSMRLogEvent [streamName=").append(streamName).append(", offset=")
        .append(offset).append(", type=").append(type).append(", entries=").append(entries)
        .append("]");
    return builder.toString();
  }

}
