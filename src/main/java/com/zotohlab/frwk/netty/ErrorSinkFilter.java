// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013, Ken Leung. All rights reserved.

package com.zotohlab.frwk.netty;

import static com.zotohlab.frwk.netty.NettyFW.getAttr;
import static com.zotohlab.frwk.netty.NettyFW.replyXXX;
import static com.zotohlab.frwk.util.CoreUtils.nsb;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelPipeline;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.util.AttributeKey;


import static java.lang.invoke.MethodHandles.*;
import org.slf4j.Logger;
import static org.slf4j.LoggerFactory.*;

/**
 * @author kenl
 */
@SuppressWarnings("rawtypes")
@ChannelHandler.Sharable
public class ErrorSinkFilter extends SimpleChannelInboundHandler {

  public static final AttributeKey<String> MSGTYPE = AttributeKey.valueOf("MSGTYPE");
  
  private static final ErrorSinkFilter shared = new ErrorSinkFilter();
  private static Logger _log = getLogger(lookup().lookupClass());
  public Logger tlog() { return _log; }

  public static ChannelPipeline addLast(ChannelPipeline pipe) {
    pipe.addLast(ErrorSinkFilter.class.getSimpleName(), shared);
    return pipe;
  }

  protected ErrorSinkFilter() {}

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    maybeHandleError(ctx);
  }

  public void exceptionCaught(ChannelHandlerContext ctx, Throwable t) throws Exception {
    tlog().error("", t);
    maybeHandleError(ctx);
  }

  private void maybeHandleError(ChannelHandlerContext ctx) throws Exception {
    Object obj = getAttr(ctx, MSGTYPE);
    if ("wsock".equals( nsb(obj)))
    {}
    else {
      replyXXX(ctx.channel(), 500);
    }
  }

}

