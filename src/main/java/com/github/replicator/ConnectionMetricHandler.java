package com.github.replicator;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;

/**
 * Metric handler for connections to keep track of how we're doing on the server.
 * 
 * @author gaurav
 */
public class ConnectionMetricHandler extends ChannelInboundHandlerAdapter {
  private final AtomicInteger currentActiveConnectionCount;
  private final AtomicLong allAcceptedConnectionCount;

  public ConnectionMetricHandler(final AtomicInteger currentActiveConnectionCount,
      final AtomicLong allAcceptedConnectionCount) {
    this.currentActiveConnectionCount = currentActiveConnectionCount;
    this.allAcceptedConnectionCount = allAcceptedConnectionCount;
  }

  @Override
  public void channelActive(ChannelHandlerContext context) throws Exception {
    allAcceptedConnectionCount.incrementAndGet();
    currentActiveConnectionCount.incrementAndGet();
    super.channelActive(context);
  }

  @Override
  public void channelInactive(ChannelHandlerContext context) throws Exception {
    currentActiveConnectionCount.decrementAndGet();
    super.channelInactive(context);
  }

  public int getCurrentActiveConnectionCount() {
    return currentActiveConnectionCount.get();
  }

  public long getAllAcceptedConnectionCount() {
    return allAcceptedConnectionCount.get();
  }

}
