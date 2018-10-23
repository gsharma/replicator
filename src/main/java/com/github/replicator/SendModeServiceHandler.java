package com.github.replicator;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

import io.netty.handler.codec.http.HttpHeaderNames;
import okhttp3.ConnectionPool;
import okhttp3.HttpUrl;
import okhttp3.Interceptor;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okio.Buffer;

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
  private final CorfuDelegate corfuDelegate;

  private static OkHttpClient client =
      new OkHttpClient.Builder().connectionPool(new ConnectionPool(5, 5, TimeUnit.MINUTES))
          .addInterceptor(new LoggingInterceptor()).readTimeout(10, TimeUnit.MINUTES).build();
  private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  SendModeServiceHandler(final ReplicationServiceConfiguration config,
      final CorfuDelegate corfuDelegate) {
    this.config = config;
    this.corfuDelegate = corfuDelegate;
  }

  boolean init() {
    logger.info("Bootstrapping send mode service handler");
    running.set(true);
    // TODO: can easily make a worker per stream and improve throughput with the general caveat of
    // the available bandwidth and under the pretext that the number of streams is not crazy high
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
    logger.info("Attempting another iteration of streaming");
    try {
      final List<LogEvent> events = corfuDelegate.fetchEvents();
      if (events == null || events.isEmpty()) {
        logger.info(String.format(
            "Streamer found no events to stream; snoozing for %d seconds until next cycle",
            config.getReplicationIntervalSeconds()));
        return;
      }

      logger.info(String.format("Preparing to stream %d events", events.size()));
      final HttpUrl url = HttpUrl.parse(config.getRemoteServiceUrl());
      final ReplicationRequest replicationRequest = new ReplicationRequest();
      replicationRequest.setRequestId(Math.random());
      replicationRequest.setEvents(events);

      final String requestJson = objectMapper.writeValueAsString(replicationRequest);
      final RequestBody body = RequestBody.create(JSON, requestJson);
      final Request request = new Request.Builder().url(url)
          .header(HttpHeaderNames.CONTENT_TYPE.toString(), "application/json")
          // .header(HttpHeaderNames.CONNECTION.toString(), "close")
          // .header(HttpHeaderNames.ORIGIN.toString(), "localhost")
          .post(body).build();
      final Response response = client.newCall(request).execute();
      logger.info(response);

      // TODO: depending on the failure modes, we should/could retry. On persistent and hard
      // failures, we also need rollback the stream to allow re-fetching of these previously fetched
      // events.

      // TODO: parse response
      // int responseCode = response.code();
      // ReplicationResponse replicationResponse =
      // objectMapper.readValue(response.body().bytes(), ReplicationResponse.class);
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

  static final class LoggingInterceptor implements Interceptor {
    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
      Request request = chain.request();
      long t1 = System.nanoTime();
      logger.info(String.format("Streaming events %s on %s%n%s%s", request.url(),
          chain.connection(), request.headers(), stringifyBody(request)));
      Response response = chain.proceed(request);
      long t2 = System.nanoTime();
      logger.info(String.format("Received response for %s in %.1fms%n%scode: %d",
          response.request().url(), (t2 - t1) / 1e6d, response.headers(), response.code()));
      return response;
    }

    private static String stringifyBody(final Request request) {
      try {
        final Request copy = request.newBuilder().build();
        final Buffer buffer = new Buffer();
        copy.body().writeTo(buffer);
        return buffer.readUtf8();
      } catch (final IOException problem) {
        return "failed to stringify request body";
      }
    }
  }

}
