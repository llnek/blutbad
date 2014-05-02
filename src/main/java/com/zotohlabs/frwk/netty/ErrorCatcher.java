/*??
// This library is distributed in  the hope that it will be useful but without
// any  warranty; without  even  the  implied  warranty of  merchantability or
// fitness for a particular purpose.
// The use and distribution terms for this software are covered by the Eclipse
// Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
// can be found in the file epl-v10.html at the root of this distribution.
// By using this software in any  fashion, you are agreeing to be bound by the
// terms of this license. You  must not remove this notice, or any other, from
// this software.
// Copyright (c) 2013 Cherimoia, LLC. All rights reserved.
 ??*/

package com.zotohlabs.frwk.netty;

import static com.zotohlabs.frwk.netty.NettyFW.replyXXX;
import io.netty.channel.ChannelHandler;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.SimpleChannelInboundHandler;

/**
 * @author kenl
 */
@ChannelHandler.Sharable
public class ErrorCatcher extends SimpleChannelInboundHandler {

  private static final ErrorCatcher shared = new ErrorCatcher();
  public static ErrorCatcher getInstance() {
    return shared;
  }

  public ErrorCatcher() {
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    replyXXX( ctx.channel(), 500);
  }

}

