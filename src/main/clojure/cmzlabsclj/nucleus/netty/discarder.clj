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

  cmzlabsclj.nucleus.netty.discarder

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])
  (:use [cmzlabsclj.nucleus.util.core :only [notnil? Try!] ]
        [cmzlabsclj.nucleus.util.str :only [strim nsb hgl?] ]
        [cmzlabsclj.nucleus.netty.io])
  (:import [java.io IOException ]
           [io.netty.buffer Unpooled]
           [io.netty.channel ChannelHandlerContext Channel ChannelPipeline
                             SimpleChannelInboundHandler
                             ChannelHandler]
           [io.netty.handler.codec.http HttpHeaders
                                        HttpContent
                                        HttpRequest HttpObjectAggregator
                                        LastHttpContent HttpRequestDecoder
                                        HttpResponseEncoder]
           [io.netty.bootstrap ServerBootstrap]
           [com.zotohlab.frwk.netty PipelineConfigurator
                                    ErrorCatcher]
           [com.zotohlab.frwk.netty NettyFW]
           [com.google.gson JsonObject]))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- discardHandler ""

  ^ChannelHandler
  [callback]

  (proxy [SimpleChannelInboundHandler][]
    (channelRead0 [ c msg ]
      (let [^ChannelHandlerContext ctx c
            ch (.channel ctx) ]
        (when (instance? LastHttpContent msg)
          (NettyFW/replyXXX ch 200)
          (when (fn? callback)
            (Try! (apply callback nil))))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- discarder ""

  ^PipelineConfigurator
  [callback]

  (proxy [PipelineConfigurator][]
    (assemble [p o]
      (let [^ChannelPipeline pipe p
            ^JsonObject options o
            ssl (SSLServerHShake options) ]
        (doto pipe
          (.addLast "HttpRequestDecoder" (HttpRequestDecoder.))
          (.addLast "HttpObjectAggregator"
                    (HttpObjectAggregator. (int 1048576)))
          (.addLast "HttpResponseEncoder" (HttpResponseEncoder.))
          (.addLast "discarder" (discardHandler callback))
          (ErrorCatcher/addLast ))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeDiscardHTTPD "Just returns 200 OK"

  [^String host port ^JsonObject options callback]

  (let [ ^ServerBootstrap bs (InitTCPServer (discarder callback) options)
         ch (StartServer bs host port) ]
    { :bootstrap bs :channel ch }
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private discarder-eof nil)

