package com.github.replicator;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.replicator.ReplicationServiceTest.LoggingInterceptor;

import io.netty.handler.codec.http.HttpHeaderNames;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

/**
 * Replication service handler to support {@value ReplicationMode#SENDER} mode of operation.
 * 
 * @author gaurav
 */
final class SendModeServiceHandler {
  private static final Logger logger =
      LogManager.getLogger(SendModeServiceHandler.class.getSimpleName());
  private final AtomicBoolean running = new AtomicBoolean();

  private static final ObjectMapper objectMapper = new ObjectMapper();
  private final ReplicationServiceConfiguration config;

  private static OkHttpClient client =
      new OkHttpClient.Builder().connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
          .addInterceptor(new LoggingInterceptor()).readTimeout(10, TimeUnit.MINUTES).build();
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  SendModeServiceHandler(final ReplicationServiceConfiguration config) {
    this.config = config;
  }

  boolean init() {
    logger.info("Bootstrapping send mode service handler");
    running.set(true);
    Thread streamWorker = new Thread() {
      {
        setName("stream-worker");
      }

      @Override
      public void run() {
        while (running.get() && !isInterrupted()) {
          try {
            stream();
            sleep(TimeUnit.SECONDS.toMillis(config.getReplicationIntervalSeconds()));
          } catch (InterruptedException interrupted) {
            running.set(false);
          }
        }
      }
    };
    streamWorker.start();
    logger.info("Successfully bootstrapped send mode service handler");
    return running.get();
  }

  void stream() {
    logger.info("Beginning another round of streaming");
    try {
      final HttpUrl url = HttpUrl.parse(config.getRemoteServiceUrl());
      ReplicationRequest replicationRequest = new ReplicationRequest();
      replicationRequest.setRequestId(Math.random());
      replicationRequest.setClientTstampMillis(System.currentTimeMillis());
      String requestJson = objectMapper.writeValueAsString(replicationRequest);
      RequestBody body = RequestBody.create(JSON, requestJson);
      Request request = new Request.Builder().url(url)
          .header(HttpHeaderNames.CONTENT_TYPE.toString(), "application/json")
          // .header(HttpHeaderNames.CONNECTION.toString(), "close")
          // .header(HttpHeaderNames.ORIGIN.toString(), "localhost")
          .post(body).build();
      Response response = client.newCall(request).execute();
      logger.info(response);
      int responseCode = response.code();
      ReplicationResponse baseResponse =
          objectMapper.readValue(response.body().bytes(), ReplicationResponse.class);
    } catch (Exception problem) {
      logger.error(problem);
    }
  }

  boolean isRunning() {
    return running.get();
  }

  void tini() throws Exception {
    logger.info("Shutting down send mode service handler");
    running.set(false);
    client.dispatcher().executorService().shutdown();
    client.connectionPool().evictAll();
    logger.info("Successfully shut down send mode service handler");
  }

}
