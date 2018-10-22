package com.github.replicator;

/**
 * Replication service framework.
 * 
 * @author gaurav
 */
public interface ReplicationService {

  void start() throws Exception;

  void stop() throws Exception;

  /**
   * A simple builder to let users use fluent APIs to build Replication Service.
   * 
   * @author gaurav
   */
  public final static class ReplicationServiceBuilder {
    private ReplicationServiceConfiguration config;

    public static ReplicationServiceBuilder newBuilder() {
      return new ReplicationServiceBuilder();
    }

    public ReplicationServiceBuilder config(final ReplicationServiceConfiguration config) {
      this.config = config;
      return this;
    }

    public ReplicationService build() {
      return new ReplicationServiceImpl(config);
    }

    private ReplicationServiceBuilder() {}
  }
}
