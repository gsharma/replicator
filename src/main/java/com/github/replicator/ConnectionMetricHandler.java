package com.github.replicator;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.IdleStateEvent;

/**
 * Metric handler for connections to keep track of how we're doing on the server.
 * 
 * @author gaurav
 */
public class ConnectionMetricHandler extends ChannelInboundHandlerAdapter {
  private final AtomicInteger currentActiveConnectionCount;
  private final AtomicLong allAcceptedConnectionCount;
  private final AtomicLong allConnectionIdleTimeoutCount;

  public ConnectionMetricHandler(final AtomicInteger currentActiveConnectionCount,
      final AtomicLong allAcceptedConnectionCount, final AtomicLong allConnectionIdleTimeoutCount) {
    this.currentActiveConnectionCount = currentActiveConnectionCount;
    this.allAcceptedConnectionCount = allAcceptedConnectionCount;
    this.allConnectionIdleTimeoutCount = allConnectionIdleTimeoutCount;
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

  @Override
  public void userEventTriggered(ChannelHandlerContext context, Object event) throws Exception {
    if (event instanceof IdleStateEvent) {
      allConnectionIdleTimeoutCount.incrementAndGet();
      super.userEventTriggered(context, event);
    }
  }

  public int getCurrentActiveConnectionCount() {
    return currentActiveConnectionCount.get();
  }

  public long getAllAcceptedConnectionCount() {
    return allAcceptedConnectionCount.get();
  }

  public long getAllConnectionIdleTimeoutCount() {
    return allConnectionIdleTimeoutCount.get();
  }
}
