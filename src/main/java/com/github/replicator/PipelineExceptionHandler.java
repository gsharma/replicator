package com.github.replicator;

import java.net.SocketAddress;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import io.netty.buffer.Unpooled;
import io.netty.channel.ChannelDuplexHandler;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.DefaultFullHttpResponse;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpRequest;
import io.netty.handler.codec.http.HttpResponseStatus;
import io.netty.handler.codec.http.HttpUtil;
import io.netty.handler.codec.http.HttpVersion;
import io.netty.util.CharsetUtil;

/**
 * Handle all uncaught exceptions
 * 
 * @author gaurav
 */
public class PipelineExceptionHandler extends ChannelDuplexHandler {
  private static final Logger logger =
      LogManager.getLogger(PipelineExceptionHandler.class.getSimpleName());

  @Override
  public void exceptionCaught(ChannelHandlerContext context, Throwable cause) {
    logger.error(cause);
    final FullHttpResponse response =
        new DefaultFullHttpResponse(HttpVersion.HTTP_1_1, HttpResponseStatus.INTERNAL_SERVER_ERROR,
            Unpooled.copiedBuffer("Replication Service Error", CharsetUtil.UTF_8));
    response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
    context.write(response).addListener(ChannelFutureListener.CLOSE);
  }

  @Override
  public void connect(ChannelHandlerContext context, SocketAddress remoteAddress,
      SocketAddress localAddress, ChannelPromise promise) {
    context.connect(remoteAddress, localAddress, promise.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
          final FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
              HttpResponseStatus.INTERNAL_SERVER_ERROR,
              Unpooled.copiedBuffer("Replication Service Connection Error", CharsetUtil.UTF_8));
          response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
          context.write(response).addListener(ChannelFutureListener.CLOSE);
        }
      }
    }));
  }

  @Override
  public void write(ChannelHandlerContext context, Object message, ChannelPromise promise) {
    context.write(message, promise.addListener(new ChannelFutureListener() {
      @Override
      public void operationComplete(ChannelFuture future) {
        if (!future.isSuccess()) {
          final FullHttpResponse response = new DefaultFullHttpResponse(HttpVersion.HTTP_1_1,
              HttpResponseStatus.INTERNAL_SERVER_ERROR,
              Unpooled.copiedBuffer("Replication Service Write Error", CharsetUtil.UTF_8));
          response.headers().set(HttpHeaderNames.CONTENT_TYPE, "application/json");
          final boolean keepAlive = HttpUtil.isKeepAlive((HttpRequest) message);
          if (keepAlive) {
            // Add 'Content-Length' header only for a keep-alive connection.
            response.headers().setInt(HttpHeaderNames.CONTENT_LENGTH,
                response.content().readableBytes());
            response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
            context.write(response, context.voidPromise());
          } else {
            context.write(response).addListener(ChannelFutureListener.CLOSE);
          }
        }
      }
    }));
  }

  @Override
  public void channelReadComplete(final ChannelHandlerContext context) {
    context.flush();
  }

}
