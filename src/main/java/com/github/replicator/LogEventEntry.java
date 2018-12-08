package com.github.replicator;

import java.io.Serializable;

import org.corfudb.annotations.Accessor;
import org.corfudb.annotations.CorfuObject;
import org.corfudb.annotations.Mutator;

/**
 * Models a log event entry.
 * 
 * General CorfuObject guidelines to keep in mind:<br>
 * 1. Do not access a Corfu object's inner state directly.<br>
 * 2. Corfu object classes must be top-level.<br>
 * 3. Corfu object constructors must be without parameters.<br>
 *
 * @author gaurav
 */
@CorfuObject
public class LogEventEntry implements Serializable {
  private static final long serialVersionUID = 1L;
  private MutableOperation operation;
  private long mutationSequenceNumber;
  private Object key;
  private Class keyClass;
  private Object value;
  private Class valueClass;

  public enum MutableOperation {
    PUT("put"), PUTALL("putAll"), REMOVE("remove"), REPLACE("replace"), CLEAR("clear");

    public static MutableOperation fromMethod(final String method) {
      MutableOperation op = null;
      for (final MutableOperation candidate : MutableOperation.values()) {
        if (candidate.method.equals(method)) {
          op = candidate;
          break;
        }
      }
      return op;
    }

    private String method;

    private MutableOperation(String method) {
      this.method = method;
    }
  }

  @Accessor
  public MutableOperation getOperation() {
    return operation;
  }

  @Mutator(name = "setOperation")
  public void setOperation(final MutableOperation operation) {
    this.operation = operation;
  }

  @Accessor
  public long getMutationSequenceNumber() {
    return mutationSequenceNumber;
  }

  @Mutator(name = "setMutationSequenceNumber")
  public void setMutationSequenceNumber(final long mutationSequenceNumber) {

  }

  @Accessor
  public Object getKey() {
    return key;
  }

  @Mutator(name = "setKey")
  public void setKey(final Object key) {
    this.key = key;
  }

  @Accessor
  public Class getKeyClass() {
    return keyClass;
  }

  @Mutator(name = "setKeyClass")
  public void setKeyClass(final Class keyClass) {
    this.keyClass = keyClass;
  }

  @Accessor
  public Object getValue() {
    return value;
  }

  @Mutator(name = "setValue")
  public void setValue(final Object value) {
    this.value = value;
  }

  @Accessor
  public Class getValueClass() {
    return valueClass;
  }

  @Mutator(name = "setValueClass")
  public void setValueClass(final Class valueClass) {
    this.valueClass = valueClass;
  }

  @Override
  public int hashCode() {
    final int prime = 31;
    int result = 1;
    result = prime * result + ((key == null) ? 0 : key.hashCode());
    result = prime * result + (int) (mutationSequenceNumber ^ (mutationSequenceNumber >>> 32));
    result = prime * result + ((operation == null) ? 0 : operation.hashCode());
    result = prime * result + ((value == null) ? 0 : value.hashCode());
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
    if (!(obj instanceof LogEventEntry)) {
      return false;
    }
    LogEventEntry other = (LogEventEntry) obj;
    if (key == null) {
      if (other.key != null) {
        return false;
      }
    } else if (!key.equals(other.key)) {
      return false;
    }
    if (mutationSequenceNumber != other.mutationSequenceNumber) {
      return false;
    }
    if (operation != other.operation) {
      return false;
    }
    if (value == null) {
      if (other.value != null) {
        return false;
      }
    } else if (!value.equals(other.value)) {
      return false;
    }
    return true;
  }

  @Override
  public String toString() {
    StringBuilder builder = new StringBuilder();
    builder.append("LogEventEntry [operation=").append(operation)
        .append(", mutationSequenceNumber=").append(mutationSequenceNumber).append(", key=")
        .append(key).append(", keyClass=").append(keyClass).append(", value=").append(value)
        .append(", valueClass=").append(valueClass).append("]");
    return builder.toString();
  }

}
