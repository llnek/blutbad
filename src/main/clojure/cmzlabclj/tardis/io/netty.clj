;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2014 Cherimoia, LLC. All rights reserved.

(ns ^{ :doc ""
       :author "kenl" }

  cmzlabclj.tardis.io.netty

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])
  (:use [cmzlabclj.nucleus.util.core
         :only [Try! Stringify ThrowIOE MubleAPI MakeMMap notnil? ConvLong] ]
        [cmzlabclj.nucleus.netty.io]
        [cmzlabclj.tardis.core.sys]
        [cmzlabclj.tardis.io.core]
        [cmzlabclj.tardis.io.http]
        [cmzlabclj.tardis.io.triggers]
        [cmzlabclj.tardis.io.webss :only [MakeWSSession] ]
        [cmzlabclj.nucleus.util.str :only [hgl? nsb strim nichts?] ]
        [cmzlabclj.nucleus.net.routes :only [MakeRouteCracker RouteCracker] ]
        [cmzlabclj.nucleus.util.seqnum :only [NextLong] ]
        [cmzlabclj.nucleus.util.mime :only [GetCharset] ])
  (:import [java.net HttpCookie URI URL InetSocketAddress]
           [java.net SocketAddress InetAddress]
           [java.util ArrayList List HashMap Map]
           [com.google.gson JsonObject]
           [java.io File IOException RandomAccessFile]
           [com.zotohlab.gallifrey.io Emitter HTTPEvent HTTPResult
                                               IOSession
                                               WebSockEvent WebSockResult]
           [javax.net.ssl SSLContext]
           [java.nio.channels ClosedChannelException]
           [io.netty.handler.codec.http HttpRequest HttpResponse HttpResponseStatus
                                        CookieDecoder ServerCookieEncoder
                                        DefaultHttpResponse HttpVersion
                                        HttpRequestDecoder
                                        HttpResponseEncoder DefaultCookie
                                        HttpHeaders$Names LastHttpContent
                                        HttpHeaders Cookie QueryStringDecoder]
           [org.apache.commons.codec.net URLCodec]
           [io.netty.bootstrap ServerBootstrap]
           [io.netty.channel Channel ChannelHandler ChannelFuture
                                     ChannelFutureListener
                                     SimpleChannelInboundHandler
                                     ChannelPipeline ChannelHandlerContext]
           [io.netty.handler.stream ChunkedFile ChunkedStream ChunkedWriteHandler]
           [com.zotohlab.gallifrey.mvc WebAsset HTTPRangeInput]
           [com.zotohlab.frwk.netty NettyFW
                                    DemuxedMsg
                                    ErrorCatcher
                                    PipelineConfigurator]
           [io.netty.handler.codec.http.websocketx WebSocketFrame
                                                   BinaryWebSocketFrame
                                                   TextWebSocketFrame]
           [io.netty.buffer ByteBuf Unpooled]
           [com.zotohlab.frwk.core Hierarchial Identifiable]
           [com.zotohlab.frwk.io XData]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- javaToCookie ""

  ^Cookie
  [^HttpCookie c ^URLCodec cc]

  ;; stick with version 0, Java's HttpCookie defaults to 1 but that
  ;; screws up the Path attribute on the wire => it's quoted but
  ;; browser seems to not like it and mis-interpret it.
  ;; Netty's cookie defaults to 0, which is cool with me.
  (doto (DefaultCookie. (.getName c)
                        (.encode cc (nsb (.getValue c))))
    ;;(.setComment (.getComment c))
    (.setDomain (.getDomain c))
    (.setMaxAge (.getMaxAge c))
    (.setPath (.getPath c))
    ;;(.setDiscard (.getDiscard c))
    ;;(.setVersion (.getVersion c))
    (.setHttpOnly (.isHttpOnly c))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeClose ""

  [^HTTPEvent evt ^ChannelFuture cf]

  (when (and (not (.isKeepAlive evt))
             (notnil? cf))
    (.addListener cf ChannelFutureListener/CLOSE)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cookiesToNetty ""

  [^List cookies]

  (let [cc (URLCodec. "utf-8") ]
    (persistent! (reduce #(conj! %1
                                 (ServerCookieEncoder/encode (javaToCookie %2 cc)))
                         (transient [])
                         (seq cookies)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- netty-ws-reply ""

  [^WebSockResult res
   ^Channel ch
   ^WebSockEvent evt src]

  (let [^XData xs (.getData res)
        bits (.javaBytes xs)
        ^WebSocketFrame
        f (cond
            (.isBinary res)
            (BinaryWebSocketFrame. (Unpooled/wrappedBuffer (.javaBytes xs)))
            :else
            (TextWebSocketFrame. (.stringify xs))) ]
    (.writeAndFlush ch f)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- replyOneFile ""

  [^RandomAccessFile raf
   ^HTTPEvent evt
   ^HttpResponse rsp ]

  (let [ct (HttpHeaders/getHeader rsp "content-type")
        rv (.getHeaderValue evt "range") ]
    (if (cstr/blank? rv)
      (ChunkedFile. raf)
      (let [r (HTTPRangeInput. raf ct rv)
            n (.prepareNettyResponse r rsp) ]
        (if (> n 0)
          r
          nil)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- netty-reply ""

  [^cmzlabclj.nucleus.util.core.MubleAPI res
   ^Channel ch
   ^HTTPEvent evt
   src]

  ;;(log/debug "netty-reply called by event with uri: " (.getUri evt))
  (let [cks (cookiesToNetty (.getf res :cookies))
        code (.getf res :code)
        rsp (NettyFW/makeHttpReply code)
        loc (nsb (.getf res :redirect))
        data (.getf res :data)
        hdrs (.getf res :hds) ]
    ;;(log/debug "about to reply " (.getStatus ^HTTPResult res))
    (with-local-vars [ clen 0 raf nil payload nil ]
      (doseq [[^String nm vs] (seq hdrs)]
        (when-not (= "content-length" (cstr/lower-case  nm))
          (doseq [vv (seq vs)]
            (HttpHeaders/addHeader rsp nm vv))))
      (doseq [s cks]
        (HttpHeaders/addHeader rsp HttpHeaders$Names/SET_COOKIE s) )
      (when (and (>= code 300)(< code 400))
        (when-not (cstr/blank? loc)
          (HttpHeaders/setHeader rsp "Location" loc)))
      (when (and (>= code 200)
                 (< code 300)
                 (not= "HEAD" (.method evt)))
        (var-set  payload
                  (condp instance? data
                    WebAsset (let [^WebAsset ws data ]
                               (HttpHeaders/setHeader rsp
                                                      "content-type"
                                                      (.contentType ws))
                               (var-set raf
                                        (RandomAccessFile. (.getFile ws) "r"))
                               (replyOneFile @raf evt rsp))

                    File (do
                           (var-set raf 
                                    (RandomAccessFile. ^File data "r"))
                           (replyOneFile @raf evt rsp))

                    XData (let [^XData xs data ] 
                            (var-set clen (.size xs))
                            (ChunkedStream. (.stream xs)))

                    (if (notnil? data)
                      (let [xs (XData. data) ]
                        (var-set clen (.size xs))
                        (ChunkedStream. (.stream xs)))
                      nil)))

        (if (and (notnil? @payload)
                 (notnil? @raf))
          (var-set clen (.length ^RandomAccessFile @raf))))

      (when (.isKeepAlive evt)
        (HttpHeaders/setHeader rsp "Connection" "keep-alive"))

      (log/debug "writing out " @clen " bytes back to client");
      (HttpHeaders/setContentLength rsp @clen)

      (.write ch rsp)
      (log/debug "wrote response headers out to client")

      (when (and (> @clen 0)
                 (notnil? @payload))
        (.write ch @payload)
        (log/debug "wrote response body out to client"))

      (let [wf (.writeAndFlush ch LastHttpContent/EMPTY_LAST_CONTENT) ]
        (log/debug "flushed last response content out to client")
        (.addListener wf
                      (reify ChannelFutureListener
                        (operationComplete [_ ff]
                          (Try! (when (notnil? @raf)
                                      (.close ^RandomAccessFile @raf))))))
        (when-not (.isKeepAlive evt)
          (log/debug "keep-alive == false, closing channel.  bye.")
          (.addListener wf ChannelFutureListener/CLOSE))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeNettyTrigger ""

  ^cmzlabclj.tardis.io.core.AsyncWaitTrigger
  [^Channel ch evt src]

  (reify AsyncWaitTrigger

    (resumeWithResult [_ res]
      (if (instance? WebSockEvent evt)
        (Try! (netty-ws-reply res ch evt src) )
        (Try! (netty-reply res ch evt src) ) ))

    (resumeWithError [_]
      (let [rsp (NettyFW/makeHttpReply 500) ]
        (try
          (maybeClose evt (.writeAndFlush ch rsp))
          (catch ClosedChannelException e#
            (log/warn "ClosedChannelException thrown while flushing headers"))
          (catch Throwable t# (log/error t# "") )) ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- cookieToJava ""

  [^Cookie c ^URLCodec cc]

  (doto (HttpCookie. (.getName c)
                     (.decode cc (nsb (.getValue c))))
    (.setComment (.getComment c))
    (.setDomain (.getDomain c))
    (.setMaxAge (.getMaxAge c))
    (.setPath (.getPath c))
    (.setVersion (.getVersion c))
    (.setHttpOnly (.isHttpOnly c))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeWSockEvent ""

  [^cmzlabclj.tardis.io.core.EmitterAPI co
   ^Channel ch
   ^XData xdata
   ^JsonObject info wantSecure]

  (let [ssl (notnil? (.get (.pipeline ch) "ssl"))
        ;;^InetSocketAddress laddr (.localAddress ch)
        res (MakeWSockResult co)
        impl (MakeMMap)
        eeid (NextLong) ]
    (with-meta
      (reify
        MubleAPI

        (setf! [_ k v] (.setf! impl k v) )
        (seq* [_] (.seq* impl))
        (getf [_ k] (.getf impl k) )
        (clrf! [_ k] (.clrf! impl k) )
        (clear! [_] (.clear! impl))

        Identifiable
        (id [_] eeid)

        WebSockEvent
        (bindSession [_ s] (.setf! impl :ios s))
        (getSession [_] (.getf impl :ios))
        (getId [_] eeid)
        (checkAuthenticity [_] wantSecure)
        (isSSL [_] ssl)
        (isText [_] (instance? String (.content xdata)))
        (isBinary [this] (not (.isText this)))
        (getData [_] xdata)
        (getResultObj [_] res)
        (replyResult [this]
          (let [^cmzlabclj.tardis.io.core.WaitEventHolder
                wevt (.release co this) ]
            (when-not (nil? wevt)
              (.resumeOnResult wevt res))))
        (emitter [_] co))

      { :typeid :czc.tardis.io/WebSockEvent }

  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- crackCookies ""

  ^Map
  [info]

  (let [v (nsb (GetHeader info "Cookie"))
        cc (URLCodec. "utf-8")
        rc (HashMap.)
        cks (if (hgl? v) (CookieDecoder/decode v) []) ]
    (doseq [^Cookie c (seq cks) ]
      (.put rc (cstr/lower-case (.getName c))
               (cookieToJava c cc)))
    rc
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent2 ""

  ^HTTPEvent
  [^cmzlabclj.tardis.io.core.EmitterAPI co
   ^Channel ch
   sslFlag
   ^XData xdata
   ^JsonObject info wantSecure]

  (let [^InetSocketAddress laddr (.localAddress ch)
        ^HTTPResult res (MakeHttpResult co)
        ^Map cookieJar (crackCookies info)
        impl (MakeMMap)
        eeid (NextLong) ]
    (with-meta
      (reify

        MubleAPI

        (setf! [_ k v] (.setf! impl k v) )
        (seq* [_] (.seq* impl))
        (getf [_ k] (.getf impl k) )
        (clrf! [_ k] (.clrf! impl k) )
        (clear! [_] (.clear! impl))

        Identifiable
        (id [_] eeid)

        HTTPEvent
        (bindSession [_ s] (.setf! impl :ios s))
        (getSession [_] (.getf impl :ios))
        (getId [_] eeid)
        (emitter [_] co)
        (checkAuthenticity [_] wantSecure)

        (getCookie [_ nm] (.get cookieJar (cstr/lower-case nm)))
        (getCookies [_] (ArrayList. (.values cookieJar)))

        (isKeepAlive [_] (-> (.get info "keep-alive")(.getAsBoolean)))

        (hasData [_] (notnil? xdata))
        (data [_] xdata)

        (contentLength [_] (-> (.get info "clen")(.getAsLong)))
        (contentType [_] (GetHeader info "content-type"))

        (encoding [this]  (GetCharset (.contentType this)))
        (contextPath [_] "")

        (getHeaderValues [_ nm] (NettyFW/getHeaderValues info nm))
        (getHeaders [_] (NettyFW/getHeaderNames info))
        (getHeaderValue [_ nm] (GetHeader info nm))
        (hasHeader [_ nm] (HasHeader? info nm))

        (getParameterValues [_ nm] (NettyFW/getParameterValues info nm))
        (getParameterValue [_ nm] (GetParameter info nm))
        (getParameters [_] (NettyFW/getParameters info))
        (hasParameter [_ nm] (HasParam? info nm))

        (localAddr [_] (.getHostAddress (.getAddress laddr)))
        (localHost [_] (.getHostName laddr))
        (localPort [_] (.getPort laddr))

        (protocol [_] (-> (.get info "protocol")(.getAsString)))
        (method [_] (-> (.get info "method")(.getAsString)))

        (queryString [_] (-> (.get info "query")(.getAsString)))
        (host [_] (-> (.get info "host")(.getAsString)))

        (remotePort [_] (ConvLong (GetHeader info "remote_port") 0))
        (remoteAddr [_] (nsb (GetHeader info "remote_addr")))
        (remoteHost [_] "")

        (scheme [_] (if sslFlag "https" "http"))

        (serverPort [_] (ConvLong (GetHeader info "server_port") 0))
        (serverName [_] (nsb (GetHeader info "server_name")))

        (isSSL [_] sslFlag)

        (getUri [_] (-> (.get info "uri")(.getAsString)))

        (getRequestURL [_] (throw (IOException. "not implemented")))

        (getResultObj [_] res)
        (replyResult [this]
          (let [^IOSession mvs (.getSession this)
                code (.getStatus res)
                ^cmzlabclj.tardis.io.core.WaitEventHolder
                wevt (.release co this) ]
            (cond
              (and (>= code 200)(< code 400)) (.handleResult mvs this res)
              :else nil)
            (when-not (nil? wevt)
              (.resumeOnResult wevt res))))
      )

      { :typeid :czc.tardis.io/HTTPEvent }

  )) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeHttpEvent ""

  ^HTTPEvent
  [^cmzlabclj.tardis.io.core.EmitterAPI co
   ^Channel ch
   sslFlag
   ^XData xdata
   ^JsonObject info wantSecure]

  (doto (makeHttpEvent2 co ch sslFlag xdata info wantSecure)
    (.bindSession (MakeWSSession co sslFlag))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/NettyIO

  [^cmzlabclj.tardis.io.core.EmitterAPI co & args]
  (let [^cmzlabclj.nucleus.net.routes.RouteInfo
        ri (nth args 2)
        ^DemuxedMsg req (nth args 1)
        ^Channel ch (nth args 0)
        ssl (notnil? (.get (.pipeline ch) "ssl"))
        xdata (.payload req)
        sec (.isSecure? ri)
        info (.info req) ]
    (if (-> (.get info "wsock")(.getAsBoolean))
      (makeWSockEvent co ch xdata info sec)
      (makeHttpEvent co ch ssl xdata info sec))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/NettyIO

  [^cmzlabclj.tardis.core.sys.Element co cfg]

  (let [c (nsb (:context cfg)) ]
    (.setAttr! co :contextPath (strim c))
    (HttpBasicConfig co cfg)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- msgDispatcher ""

  ^ChannelHandler
  ;;[^cmzlabclj.tardis.core.sys.Element co]
  [^cmzlabclj.tardis.io.core.EmitterAPI co
   options]

  (proxy [SimpleChannelInboundHandler] []
    (channelRead0 [ctx msg]
      (let [ch (.channel ^ChannelHandlerContext ctx)
            ts (.getAttr ^cmzlabclj.tardis.core.sys.Element co :waitMillis)
            evt (IOESReifyEvent co ch msg) ]
        (if (instance? HTTPEvent evt)
          (let [^cmzlabclj.tardis.io.core.WaitEventHolder
                w (MakeAsyncWaitHolder (MakeNettyTrigger ch evt co) evt) ]
            (.timeoutMillis w ts)
            (.hold co w)))
        (.dispatch co evt {})))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- nettyInitor ""

  ^PipelineConfigurator
  [^cmzlabclj.tardis.core.sys.Element co]

  (log/debug "tardis netty pipeline initor called with emitter = " (type co))
  (ReifyHTTPPipe "NettyDispatcher" (fn [options] (msgDispatcher co options))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-netty ""

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [^cmzlabclj.tardis.core.sys.Element ctr (.parent ^Hierarchial co)
        ^JsonObject options (.getAttr co :emcfg)
        bs (InitTCPServer (nettyInitor co) options) ]
    (.setAttr! co :netty  { :bootstrap bs })
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/NettyIO

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [host (nsb (.getAttr co :host))
        port (.getAttr co :port)
        nes (.getAttr co :netty)
        ^ServerBootstrap bs (:bootstrap nes)
        ch (StartServer bs host port) ]
    (.setAttr! co :netty (assoc nes :channel ch))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/NettyIO

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [nes (.getAttr co :netty)
        ^ServerBootstrap bs (:bootstrap nes)
        ^Channel ch (:channel nes) ]
    (StopServer  bs ch)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/NettyIO

  [^cmzlabclj.tardis.core.sys.Element co]

  (init-netty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private netty-eof nil)

