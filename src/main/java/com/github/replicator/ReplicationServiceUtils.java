package com.github.replicator;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelPromise;
import io.netty.handler.codec.http.FullHttpResponse;
import io.netty.handler.codec.http.HttpHeaderValues;
import io.netty.handler.codec.http.HttpUtil;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

import java.io.IOException;
import java.net.SocketAddress;
import java.net.URL;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;

import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.http.FullHttpRequest;
import io.netty.handler.codec.http.HttpHeaderNames;
import io.netty.handler.codec.http.HttpHeaders;
import io.netty.handler.codec.http.HttpMethod;
import io.netty.handler.codec.http.QueryStringDecoder;
import io.netty.handler.codec.http.cookie.Cookie;
import io.netty.handler.codec.http.cookie.ServerCookieDecoder;

/**
 * Replication service utility functions.
 * 
 * @author gaurav
 */
final class ReplicationServiceUtils {
  public static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

  // TODO: not use hard-coded timeouts. Just set this up in a sane manner
  private static final OkHttpClient httpClient =
      new OkHttpClient.Builder().readTimeout(10, TimeUnit.SECONDS).build();

  static Response post(final URL url, final MediaType mediaType, final String body)
      throws IOException {
    RequestBody requestBody = RequestBody.create(mediaType, body);
    Request request = new Request.Builder().url(url).post(requestBody).build();
    Response response = httpClient.newCall(request).execute();
    return response;
  }

  static Response get(final URL url) throws IOException {
    Request request = new Request.Builder().url(url).get().build();
    Response response = httpClient.newCall(request).execute();
    return response;
  }

  static String readRequest(final ChannelHandlerContext context, final FullHttpRequest request) {
    StringBuilder details = new StringBuilder();

    // 1. parse uri and method
    SocketAddress localAddress = context.pipeline().channel().localAddress();
    final HttpMethod method = request.method();
    final String uri = request.uri();
    details.append(method.name() + ' ' + localAddress + uri);

    // 2. parse request headers
    final HttpHeaders requestHeaders = request.headers();
    details.append(String.format(" Request-Headers: %s", requestHeaders.entries()));

    // 3. parse query params
    final QueryStringDecoder queryStringDecoder = new QueryStringDecoder(request.uri());
    final Map<String, List<String>> queryParams = queryStringDecoder.parameters();
    details.append(String.format(" Query-Params: %s", queryParams));

    // 4. read cookies
    final String cookieString = request.headers().get(HttpHeaderNames.COOKIE);
    Set<Cookie> cookies = null;
    if (cookieString != null) {
      cookies = ServerCookieDecoder.STRICT.decode(cookieString);
    }
    details.append(String.format(" Cookies: %s", cookies));
    return details.toString();
  }

  static void channelResponseWrite(final ChannelHandlerContext channelHandlerContext,
      final FullHttpRequest fullHttpRequest, final FullHttpResponse response,
      final ChannelPromise promise) {
    final boolean keepAlive = HttpUtil.isKeepAlive(fullHttpRequest);
    if (keepAlive) {
      response.headers().set(HttpHeaderNames.CONNECTION, HttpHeaderValues.KEEP_ALIVE);
      channelHandlerContext.writeAndFlush(response, promise);
    } else {
      channelHandlerContext.writeAndFlush(response).addListener(ChannelFutureListener.CLOSE);
    }
  }

  static final Function<FullHttpRequest, String> accessTokenDecoder =
      new Function<FullHttpRequest, String>() {
        @Override
        public String apply(final FullHttpRequest request) {
          if (request.headers().contains(HttpHeaderNames.AUTHORIZATION)) {
            return request.headers().get(HttpHeaderNames.AUTHORIZATION);
          } else if (request.headers().contains("auth-token")) {
            return request.headers().get("auth-token");
          }
          return new String();
        }
      };

}
