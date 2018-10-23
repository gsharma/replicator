package com.github.replicator;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serializable;
import java.util.Map;

import org.corfudb.annotations.Accessor;
import org.corfudb.annotations.CorfuObject;
import org.corfudb.annotations.Mutator;

/**
 * Models the last set of offsets that have already been streamed. These serve as the start offsets
 * for the subsequent iteration of replication.
 * 
 * General CorfuObject guidelines to keep in mind:<br>
 * 1. Do not access a Corfu object's inner state directly.<br>
 * 2. Corfu object classes must be top-level.<br>
 * 3. Corfu object constructors must be without parameters.<br>
 *
 * @author Gaurav
 */
@CorfuObject
public class StreamedOffsets implements Serializable {
  private static final long serialVersionUID = 1L;
  public static final String STREAM_NAME = "StreamedOffsets";
  private Map<String, Long> lastStreamed;

  @Accessor
  public Map<String, Long> getLastStreamed() {
    return lastStreamed;
  }

  @Mutator(name = "setLastStreamed")
  public void setLastStreamed(Map<String, Long> lastStreamed) {
    this.lastStreamed = lastStreamed;
  }

  public static byte[] serialize(final StreamedOffsets event) throws IOException {
    ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
    new ObjectOutputStream(byteStream).writeObject(event);
    return byteStream.toByteArray();
  }

  public static StreamedOffsets deserialize(byte[] event)
      throws IOException, ClassNotFoundException {
    ByteArrayInputStream byteStream = new ByteArrayInputStream(event);
    return (StreamedOffsets) new ObjectInputStream(byteStream).readObject();
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((lastStreamed == null) ? 0 : lastStreamed.hashCode());
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
    if (!(obj instanceof StreamedOffsets)) {
      return false;
    }
    StreamedOffsets other = (StreamedOffsets) obj;
    if (lastStreamed == null) {
      if (other.lastStreamed != null) {
        return false;
      }
    } else if (!lastStreamed.equals(other.lastStreamed)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("StreamedOffsets [lastStreamed=").append(lastStreamed).append("]");
    return builder.toString();
  }

}
