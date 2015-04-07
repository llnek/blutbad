;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tardis.mvc.filters

  (:require [clojure.tools.logging :as log :only [info warn error debug]]
            [clojure.string :as cstr])

  (:use [czlabclj.xlib.util.core
         :only
         [notnil? spos? ToJavaInt MubleAPI Try! NiceFPath]]
        [czlabclj.xlib.netty.io]
        [czlabclj.tardis.io.triggers]
        [czlabclj.tardis.io.http :only [HttpBasicConfig]]
        [czlabclj.tardis.io.netty]
        [czlabclj.tardis.io.core]
        [czlabclj.tardis.core.sys]
        [czlabclj.tardis.core.constants]
        [czlabclj.tardis.mvc.assets
         :only
         [SetCacheAssetsFlag GetLocalFile ReplyFileAsset]]
        [czlabclj.tardis.mvc.comms]
        [czlabclj.xlib.util.str :only [hgl? nsb strim]]
        [czlabclj.xlib.util.meta :only [MakeObj]]
        [czlabclj.xlib.net.routes])

  (:import  [org.apache.commons.lang3 StringUtils]
            [io.netty.util ReferenceCountUtil]
            [java.util Date]
            [java.io File]
            [com.zotohlab.frwk.io XData]
            [com.google.gson JsonObject]
            [com.zotohlab.frwk.core Hierarchial Identifiable]
            [com.zotohlab.gallifrey.io HTTPEvent Emitter]
            [com.zotohlab.gallifrey.mvc HTTPErrorHandler
             MVCUtils WebAsset WebContent]
            [io.netty.handler.codec.http HttpRequest
             HttpResponse
             CookieDecoder ServerCookieEncoder
             DefaultHttpResponse HttpVersion
             HttpResponseEncoder HttpRequestDecoder
             HttpHeaders LastHttpContent
             HttpHeaders Cookie QueryStringDecoder]
            [io.netty.bootstrap ServerBootstrap]
            [io.netty.channel Channel ChannelHandler
             ChannelDuplexHandler
             SimpleChannelInboundHandler
             ChannelPipeline ChannelHandlerContext]
            [io.netty.handler.stream ChunkedWriteHandler]
            [io.netty.util AttributeKey]
            [io.netty.handler.timeout IdleState
             IdleStateEvent
             IdleStateHandler]
            [com.zotohlab.frwk.netty NettyFW ErrorSinkFilter
             SimpleInboundFilter
             DemuxedMsg PipelineConfigurator
             FlashFilter]
            [jregex Matcher Pattern]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; leiningen compile throws errors, probably compiling twice ???
(try
(def ^:private GOOD_FLAG (AttributeKey/valueOf "good-msg"))
(catch Throwable e#))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- routeFilter ""

  ^ChannelHandler
  [^czlabclj.tardis.core.sys.Element co]

  (proxy [SimpleInboundFilter] []
    (channelRead0 [c msg]
      ;;(log/debug "mvc route filter called with message = " (type msg))
      (cond
        (instance? HttpRequest msg)
        (let [^czlabclj.xlib.net.routes.RouteCracker
              ck (.getAttr co :cracker)
              ^ChannelHandlerContext ctx c
              ^HttpRequest req msg
              ch (.channel ctx)
              cfg {:method (NettyFW/getMethod req)
                   :uri (NettyFW/getUriPath req)}
              [r1 r2 r3 r4]
              (.crack ck cfg) ]
          (-> (.attr ctx GOOD_FLAG)(.remove))
          (cond
            (and r1 (hgl? r4))
            (NettyFW/sendRedirect ch false ^String r4)

            (= r1 true)
            (do
              ;;(log/debug "mvc route filter MATCHED with uri = " (.getUri req))
              (-> (.attr ctx GOOD_FLAG)(.set "matched"))
              (ReferenceCountUtil/retain msg)
              (.fireChannelRead ctx msg))

            :else
            (do
              (log/debug "Failed to match uri: " (.getUri req))
              (NettyFW/replyXXX ch 404 false))))

        (instance? HttpResponse msg)
        (do
          (ReferenceCountUtil/retain msg)
          (.fireChannelRead ^ChannelHandlerContext c msg))

        :else
        (let [^ChannelHandlerContext ctx c
              flag (-> (.attr ctx GOOD_FLAG)(.get)) ]
          (if (notnil? flag)
            (do
              (ReferenceCountUtil/retain msg)
              (.fireChannelRead ctx msg))
            (log/debug "Skipping unwanted msg")))
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mvcDispatcher ""

  ^ChannelHandler
  [^czlabclj.tardis.io.core.EmitAPI em
   ^czlabclj.tardis.core.sys.Element co]

  (proxy [SimpleInboundFilter] []
    (channelRead0 [c msg]
      ;;(log/debug "mvc netty handler called with message = " (type msg))
      (let [^czlabclj.xlib.net.routes.RouteCracker
            rcc (.getAttr co :cracker)
            ^ChannelHandlerContext ctx c
            ch (.channel ctx)
            info (.info ^DemuxedMsg msg)
            [r1 r2 r3 r4] (.crack rcc info)
            ^czlabclj.xlib.net.routes.RouteInfo ri r2]
        (cond
          (= r1 true)
          (let [^HTTPEvent evt (IOESReifyEvent co ch msg ri) ]
            (log/debug "Matched one route: " (.getPath ri)
                       " , and static = " (.isStatic? ri))
            (if (.isStatic? ri)
              (ServeStatic ri co r3 ch info evt)
              (ServeRoute ri co r3 ch evt)))
          :else
          (do
            (log/debug "Failed to match uri: " (:uri info))
            (ServeError co ch 404)) )))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- reifyIdleStateFilter ""

  ^ChannelHandler
  []

  (proxy [ChannelDuplexHandler][]
    (userEventTriggered[ctx msg]
      (when-let [^IdleStateEvent
                 evt (if (instance? IdleStateEvent msg)
                       msg
                       nil) ]
        (condp == (.state evt)
          IdleState/READER_IDLE
          (-> (.channel ^ChannelHandlerContext ctx)
              (.close))
          IdleState/WRITER_IDLE
          nil ;; (.writeAndFlush ch (PingMessage.))
          (log/warn "Not sure what is going on here?"))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- wsockDispatcher ""

  ^ChannelHandler
  [^czlabclj.tardis.io.core.EmitAPI em
   ^czlabclj.tardis.core.sys.Element co
   options]

  (let [handlerFn (-> (:wsock options)
                      (:handler )) ]
    (log/debug "wsockDispatcher has user function: " handlerFn)
    (proxy [SimpleInboundFilter] []
        (channelRead0 [ctx msg]
          (let [ch (.channel ^ChannelHandlerContext ctx)
                opts {:router handlerFn}
                ^WebSockEvent
                evt (IOESReifyEvent co ch msg nil) ]
            (log/debug "Reified one websocket event")
            (.dispatch em evt opts))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mvcInitorOnHttp ""

  [^ChannelHandlerContext ctx hack options]

  (let [^ChannelPipeline pipe (.pipeline ctx)
        co (:emitter hack) ]
    (.addAfter pipe
               "HttpResponseEncoder"
               "ChunkedWriteHandler" (ChunkedWriteHandler.))
    (log/info "mvcInitorOnHttp: pipeline() = " (NettyFW/dbgPipelineHandlers pipe))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mvcInitorOnWS ""

  [^ChannelHandlerContext ctx hack options]

  (let [^ChannelPipeline pipe (.pipeline ctx)
        co (:emitter hack) ]
    (.addBefore pipe
                "ErrorSinkFilter"
                "WSOCKDispatcher"
                (wsockDispatcher co co options))
    (-> (.attr ctx ErrorSinkFilter/MSGTYPE)
        (.set "wsock"))
    ;;(.remove pipe "ChunkedWriteHandler")
    (.remove pipe "MVCDispatcher")
    (.remove pipe "RouteFilter")
    (log/info "mvcInitorOnWS: pipeline() = " (NettyFW/dbgPipelineHandlers pipe))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mvcInitor ""

  ^PipelineConfigurator
  [^czlabclj.tardis.core.sys.Element co options1]

  (let [hack {:onhttp mvcInitorOnHttp
              :onwsock mvcInitorOnWS
              :emitter co} ]
    (log/debug "MVC netty pipeline initor called with emitter = " (type co))
    (proxy [PipelineConfigurator] []
      (assemble [p options]
        (let [^ChannelPipeline pipe p
              ssl (SSLServerHShake options) ]
          ;; should flash go before ssl, probably...
          (FlashFilter/addLast pipe)
          (when-not (nil? ssl)
            (.addLast pipe "ssl" ssl))
          (doto pipe
            ;;(.addLast "IdleStateHandler" (IdleStateHandler. 100 100 100))
            (.addLast "HttpRequestDecoder" (HttpRequestDecoder.))
            (.addLast "RouteFilter" (routeFilter co))
            (.addLast "HttpDemuxFilter" (MakeHttpDemuxFilter options hack))
            (.addLast "HttpResponseEncoder" (HttpResponseEncoder.))
            ;;(.addLast "ChunkedWriteHandler" (ChunkedWriteHandler.))
            (.addLast "MVCDispatcher" (mvcDispatcher co co))
            (ErrorSinkFilter/addLast ))
          pipe)))
    ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- init-netty ""

  [^czlabclj.tardis.core.sys.Element co]

  (let [^czlabclj.tardis.core.sys.Element
        ctr (.parent ^Hierarchial co)
        rts (.getAttr ctr :routes)
        options (.getAttr co :emcfg)
        bs (InitTCPServer (mvcInitor co options) options) ]
    (.setAttr! co :cracker (MakeRouteCracker rts))
    (.setAttr! co :netty  { :bootstrap bs })
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/NettyMVC

  [^czlabclj.tardis.core.sys.Element co cfg]

  (log/info "CompConfigure: NetttyMVC: " (.id ^Identifiable co))
  (.setAttr! co :emcfg (HttpBasicConfig co cfg))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/NettyMVC

  [^czlabclj.tardis.core.sys.Element co]

  (log/info "CompInitialize: NetttyMVC: " (.id ^Identifiable co))
  (init-netty co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private filters-eof nil)
