package com.github.replicator;

import static org.junit.Assert.assertTrue;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.corfudb.runtime.collections.CorfuTable;
import org.corfudb.util.serializer.Serializers;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import com.github.replicator.LogEvent.Component;
import com.github.replicator.LogEvent.Status;
import com.github.replicator.ReplicationService.ReplicationServiceBuilder;

import okhttp3.Interceptor;
import okhttp3.Request;
import okhttp3.Response;

/**
 * Tests for Replication Service, lots more to do.
 * 
 * @author gaurav
 */
public class ReplicationServiceTest {
  private static final Logger logger =
      LogManager.getLogger(ReplicationServiceTest.class.getSimpleName());

  // Receiver
  private static ReplicationServiceConfiguration receiverConfig =
      new ReplicationServiceConfiguration("localhost", 9002, 2,
          Runtime.getRuntime().availableProcessors(), 120, 120, 9, ReplicationMode.RECEIVER,
          "localhost", 9005, 5L, 0L, 9, null);
  private static ReplicationService receiverService =
      ReplicationServiceBuilder.newBuilder().config(receiverConfig).build();

  // Sender
  private static ReplicationServiceConfiguration senderConfig =
      new ReplicationServiceConfiguration("localhost", 8002, 2,
          Runtime.getRuntime().availableProcessors(), 15, 15, 9, ReplicationMode.TRANSMITTER,
          "localhost", 8005, 5L, 0L, 9, "http://localhost:9002/service/replicator/");
  private static ReplicationService senderService =
      ReplicationServiceBuilder.newBuilder().config(senderConfig).build();

  private static LocalCorfuServer corfuServerSender;

  private static LocalCorfuServer corfuServerReceiver;

  @Test
  public void testReplication() throws Exception {
    // 1. setup Receiver mode server in a thread with its own http, corfu servers

    // 2. setup Send mode server in another thread with its own http, corfu servers - give sender
    // the receiver http address for streaming

    // 3. use local corfu of Send mode server to push log events
    CorfuDelegate senderCorfuDelegate = new CorfuDelegate();
    senderCorfuDelegate.init(senderConfig);
    int eventCount = 30;
    final List<LogEvent> events = new ArrayList<>(eventCount);
    for (int iter = 0; iter < eventCount; iter++) {
      LogEvent event = new LogEvent();
      event.setClientTstamp(System.nanoTime());
      event.setComponent(Component.HTTP_SERVER);
      event.setStatus(Status.UP);
      events.add(event);
    }
    // logger.info(String.format("Pumping %d test replication events", eventCount));
    // senderCorfuDelegate.saveEvents(events);

    logger.info("Starting corfu table operations");
    final Map<String, String> testMap = getMap(senderCorfuDelegate, LogEvent.STREAM_NAME);
    senderCorfuDelegate.getRuntime().getObjectsView().TXBegin();
    testMap.put("ONE", "1");
    testMap.put("TWO", "2");
    testMap.put("ONE", "11");
    testMap.put("THREE", "3");
    testMap.remove("ONE");
    testMap.clear();
    senderCorfuDelegate.getRuntime().getObjectsView().TXEnd();

    // 4. breather for Receiver to receive and save events (apply log)
    Thread.sleep(5_000L);
  }

  private <K, V> Map<K, V> getMap(final CorfuDelegate delegate, final String streamName) {
    return delegate.getRuntime().getObjectsView().build().setType(CorfuTable.class)
        .setStreamName(streamName).setSerializer(Serializers.JSON).open();
  }
  /*
   * @Test public void testPushReplicator() throws Exception { final Runnable work = new Runnable()
   * { public void run() { try { final HttpUrl url = new
   * HttpUrl.Builder().scheme("http").host("localhost").addPathSegment("service")
   * .addPathSegment("replicator").port(senderConfig.getServerPort()).build(); // hydrate with many
   * events ReplicationRequest replicationRequest = new ReplicationRequest();
   * replicationRequest.setRequestId(Math.random()); int eventCount = 10; final List<LogEvent>
   * events = new ArrayList<>(eventCount); for (int iter = 0; iter < eventCount; iter++) { LogEvent
   * event = new LogEvent(); event.setClientTstamp(System.nanoTime());
   * event.setComponent(Component.HTTP_SERVER); event.setStatus(Status.UP); events.add(event); }
   * replicationRequest.setEvents(events);
   * 
   * String requestJson = objectMapper.writeValueAsString(replicationRequest); RequestBody body =
   * RequestBody.create(JSON, requestJson); Request request = new Request.Builder().url(url)
   * .header(HttpHeaderNames.CONTENT_TYPE.toString(), "application/json") //
   * .header(HttpHeaderNames.CONNECTION.toString(), "close")
   * .header(HttpHeaderNames.ORIGIN.toString(), "localhost").post(body).build(); Response response =
   * client.newCall(request).execute(); assertEquals(200, response.code());
   * 
   * request = new Request.Builder().url(url) .header(HttpHeaderNames.CONTENT_TYPE.toString(),
   * "application/json") .header("Origin", "localhost").get().build(); response =
   * client.newCall(request).execute(); assertEquals(200, response.code()); ReplicationResponse
   * replicationResponse = objectMapper.readValue(response.body().bytes(),
   * ReplicationResponse.class); assertNotNull(replicationResponse);
   * assertTrue(replicationResponse.getServerTstampMillis() != 0); } catch (Exception fooBar) {
   * logger.error(fooBar); } } }; int workerCount = 1; Thread[] workers = new Thread[workerCount];
   * for (int iter = 0; iter < workerCount; iter++) { Thread worker = new Thread(work, "client-" +
   * iter); workers[iter] = worker; worker.start(); } for (Thread worker : workers) { worker.join();
   * } Thread.sleep(15_000L); }
   */

  @BeforeClass
  public static void init() throws Exception {
    try {
      // receiver init
      logger.info("Bootstrapping Receiver");
      corfuServerReceiver =
          new LocalCorfuServer(receiverConfig.getCorfuHost(), receiverConfig.getCorfuPort());
      corfuServerReceiver.init();
      assertTrue(corfuServerReceiver.isRunning());
      receiverService.start();
      logger.info("Bootstrapped Receiver");

      // sender init
      logger.info("Bootstrapping Sender");
      corfuServerSender =
          new LocalCorfuServer(senderConfig.getCorfuHost(), senderConfig.getCorfuPort());
      corfuServerSender.init();
      assertTrue(corfuServerSender.isRunning());
      senderService.start();
      logger.info("Bootstrapped Sender");
    } catch (IOException problem) {
      logger.error(problem);
    }
  }

  @AfterClass
  public static void tini() throws Exception {
    // sender tini
    if (senderService != null) {
      senderService.stop();
    }
    assertTrue(corfuServerSender.isRunning());
    corfuServerSender.tini();
    assertTrue(!corfuServerSender.isRunning());
    logger.info("Successfully shutdown Sender");

    // receiver tini
    if (receiverService != null) {
      receiverService.stop();
    }
    assertTrue(corfuServerReceiver.isRunning());
    corfuServerReceiver.tini();
    assertTrue(!corfuServerReceiver.isRunning());
    logger.info("Successfully shutdown Receiver");
  }

  static final class LoggingInterceptor implements Interceptor {
    @Override
    public Response intercept(Interceptor.Chain chain) throws IOException {
      Request request = chain.request();
      long t1 = System.nanoTime();
      logger.info(String.format("Sending request %s on %s%n%s", request.url(), chain.connection(),
          request.headers()));
      Response response = chain.proceed(request);
      long t2 = System.nanoTime();
      logger.info(String.format("Received response for %s in %.1fms%n%s", response.request().url(),
          (t2 - t1) / 1e6d, response.headers()));
      return response;
    }
  }

}
