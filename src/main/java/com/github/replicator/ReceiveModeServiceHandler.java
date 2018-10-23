package com.github.replicator;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.handler.codec.http.HttpResponseStatus;

import static io.netty.handler.codec.http.HttpResponseStatus.*;
import static io.netty.handler.codec.http.HttpVersion.*;

import java.nio.charset.StandardCharsets;
import java.util.List;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Replication service handler to support {@value ReplicationMode#RECEIVER} mode of operation.
 * 
 * @author gaurav
 */
final class ReceiveModeServiceHandler extends SimpleChannelInboundHandler<FullHttpRequest> {
  private static final Logger logger =
      LogManager.getLogger(ReceiveModeServiceHandler.class.getSimpleName());
  private static final ObjectMapper objectMapper = new ObjectMapper();

  private final CorfuDelegate corfuDelegate;

  ReceiveModeServiceHandler(final CorfuDelegate corfuDelegate) {
    this.corfuDelegate = corfuDelegate;
  }

  @Override
  public void channelRead0(final ChannelHandlerContext context, final FullHttpRequest request)
      throws Exception {
    // logger.info(String.format("Received %s message", request.getClass().getSimpleName()));

    if (HttpUtil.is100ContinueExpected(request)) {
      send100Continue(context);
      return;
    }

    logger
        .info(String.format("Received %s", ReplicationServiceUtils.readRequest(context, request)));

    // TODO: better handling of Supported methods, uri, content-types
    final HttpMethod method = request.method();
    final String uri = request.uri().trim();
    final String contentType = request.headers().get("Content-Type");
    final ByteBuf content = request.content();
    final String body = content != null ? content.toString(StandardCharsets.UTF_8) : "";

    FullHttpResponse response = null;

    if (method == HttpMethod.POST) {
      if (uri.endsWith("service/replicator") || uri.endsWith("service/replicator/")) {
        ReplicationRequest replicationRequest =
            objectMapper.readValue(body, ReplicationRequest.class);
        if (replicationRequest != null) {
          // logger.info("Received " + replicationRequest);
          List<LogEvent> events = replicationRequest.getEvents();
          if (events != null && !events.isEmpty()) {
            corfuDelegate.saveEvents(events);
          }

          final ReplicationResponse replicationResponse = new ReplicationResponse();
          replicationResponse.setAckEventsCount(events.size());
          final String responseJson = objectMapper.writeValueAsString(replicationResponse);
          logger.info("Replication round completion response: " + responseJson);
          ByteBuf statusBytes = context.alloc().buffer();
          statusBytes.writeBytes(responseJson.getBytes());
        }
      }
      response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK);
    }

    if (method == HttpMethod.GET) {
      if (uri.endsWith("service/replicator") || uri.endsWith("service/replicator/")) {
        ReplicationResponse replicationResponse = new ReplicationResponse();
        replicationResponse.setServerTstampMillis(System.currentTimeMillis());
        String responseJson = objectMapper.writeValueAsString(replicationResponse);
        logger.info("Streaming back: " + responseJson);
        ByteBuf statusBytes = context.alloc().buffer();
        statusBytes.writeBytes(responseJson.getBytes());
        response =
            new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.OK, statusBytes);
      }
    }

    if (response == null) {
      response =
          new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.METHOD_NOT_ALLOWED);
    }

    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
    response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH, response.content().readableBytes());

    // CORS Headers, if needed - tweak here or use CorsHandler
    // response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_ORIGIN, "*");
    // response.headers().set(HttpHeaderNames.ACCESS_CONTROL_ALLOW_METHODS, "GET, POST");

    ReplicationServiceUtils.channelResponseWrite(context, request, response, context.voidPromise());
  }

  private static void send100Continue(final ChannelHandlerContext context) {
    final FullHttpResponse response = new DefaultFullHttpResponse(HTTP_1_1, CONTINUE);
    context.write(response);
  }

  @Override
  public void exceptionCaught(final ChannelHandlerContext context, final Throwable cause) {
    logger.error(cause);
    context.close();
  }

  @Override
  public void channelReadComplete(final ChannelHandlerContext context) {
    context.flush();
  }

}
