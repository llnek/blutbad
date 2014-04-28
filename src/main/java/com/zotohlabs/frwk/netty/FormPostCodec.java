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

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.zotohlabs.frwk.io.IOUtils;
import com.zotohlabs.frwk.io.XData;
import com.zotohlabs.frwk.net.ULFileItem;
import com.zotohlabs.frwk.net.ULFormItems;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.CompositeByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.*;
import io.netty.handler.codec.http.*;
import io.netty.handler.codec.http.multipart.*;
import io.netty.handler.codec.http.websocketx.*;
import io.netty.handler.ssl.SslHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static com.zotohlabs.frwk.util.CoreUtils.*;
import static com.zotohlabs.frwk.io.IOUtils.*;

/**
 * @author kenl
 */
@ChannelHandler.Sharable
public class FormPostCodec extends RequestCodec {

  protected static final AttributeKey FORMDEC_KEY =AttributeKey.valueOf( "formdecoder");
  protected static final AttributeKey FORMITMS_KEY= AttributeKey.valueOf("formitems");

  public static FormPostCodec sharedHandler = new FormPostCodec();

  protected void resetAttrs(ChannelHandlerContext ctx) {
    HttpPostRequestDecoder dc = (HttpPostRequestDecoder) getAttr( ctx, FORMDEC_KEY);
    ULFormItems fis = (ULFormItems) getAttr(ctx, FORMITMS_KEY);

    delAttr(ctx,FORMITMS_KEY);
    delAttr(ctx,FORMDEC_KEY);
    if (dc != null) dc.destroy();
    if (fis != null) fis.destroy();

    super.resetAttrs(ctx);
  }

  private void handleFormPost(ChannelHandlerContext ctx , HttpRequest req) {
    DefaultHttpDataFactory fac= new DefaultHttpDataFactory(streamLimit());
    HttpPostRequestDecoder dc = new HttpPostRequestDecoder( fac, req);
    setAttr(ctx ,FORMITMS_KEY, new ULFormItems() );
    setAttr( ctx, FORMDEC_KEY, dc);
    handleFormPostChunk(ctx, req);
  }

  private void writeHttpData(ChannelHandlerContext ctx, InterfaceHttpData data) throws IOException {
    if (data==null) { return; }
    InterfaceHttpData.HttpDataType dt= data.getHttpDataType();
    ULFormItems fis = (ULFormItems) getAttr(ctx, FORMITMS_KEY);
    String nm = dt.name();
    if (dt == InterfaceHttpData.HttpDataType.FileUpload) {
      FileUpload fu = (FileUpload)data;
      String ct = fu.getContentType();
      String fnm = fu.getFilename();
      if (fu.isCompleted()) {
        if (fu instanceof DiskFileUpload) {
          File fp = (File) IOUtils.newTempFile(false)[0];
          ((DiskFileUpload) fu).renameTo(fp);
          fis.add (new ULFileItem( nm , ct, fnm , new XData( fp)));
        } else {
          Object[] fos = IOUtils.newTempFile(true);
          OutputStream os = (OutputStream)fos[1];
          File fp = (File)fos[0];
          ByteBuf buf = fu.content();
          slurpByteBuf( buf, os);
          org.apache.commons.io.IOUtils.closeQuietly(os);
          fis.add( new ULFileItem( nm , ct, fnm,  new XData( fp)));
        }
      }
    }
    else
    if (dt == InterfaceHttpData.HttpDataType.Attribute) {
      ByteArrayOutputStream baos= new ByteArrayOutputStream();
      Attribute attr =  (Attribute) data;
      slurpByteBuf(attr.content(), baos);
      fis.add(new ULFileItem( nm, baos.toByteArray() ));
    }
    else {
      throw new IOException( "Bad POST: unknown http data.");
    }
  }

  private void readHttpDataChunkByChunk(ChannelHandlerContext ctx, HttpPostRequestDecoder dc) throws IOException {
    try {
      while (dc.hasNext() ) {
        InterfaceHttpData data = dc.next();
        try {
          writeHttpData( ctx, data);
        }
        finally {
          data.release();
        }
      }
    }
    catch (HttpPostRequestDecoder.EndOfDataDecoderException e) {
      //eat it => indicates end of content chunk by chunk
    }
  }

  private void handleFormPostChunk(ChannelHandlerContext ctx, Object msg) {
    HttpPostRequestDecoder dc = (HttpPostRequestDecoder) getAttr( ctx, FORMDEC_KEY);
    Throwable err= null;
    if (msg instanceof HttpContent) {
      try {
        dc.offer( (HttpContent) msg);
        readHttpDataChunkByChunk(ctx, dc);
      } catch (Throwable e) {
        err = e;
        ctx.fireExceptionCaught(e);
      }
    }
    if (err==null && msg instanceof LastHttpContent) {
      ULFormItems fis= (ULFormItems) getAttr(ctx, FORMITMS_KEY);
      JsonObject info = (JsonObject) getAttr(ctx, MSGINFO_KEY);
      XData xs = (XData) getAttr( ctx, XDATA_KEY);
      delAttr(ctx, FORMITMS_KEY);
      xs.resetContent(fis);
      resetAttrs(ctx);
      ctx.fireChannelRead(new HashMap<String,Object>() {{
        put("payload", xs);
        put("info", info);
        }});
    }
  }

  @Override
  protected void channelRead0(ChannelHandlerContext ctx, Object msg) throws Exception {
    if (msg instanceof HttpRequest) {
      HttpRequest req = (HttpRequest) msg;
      handleFormPost(ctx, req);
    }
    else
    if (msg instanceof HttpContent) {
      HttpContent c = (HttpContent) msg;
      handleFormPostChunk(ctx,c);
    }
  }
}

