/* Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Copyright (c) 2013-2016, Kenneth Leung. All rights reserved. */


package czlab.wabbit.io;

import czlab.convoy.net.HttpResult;
import java.net.HttpCookie;
import czlab.xlib.Context;
import czlab.xlib.XData;

/**
 * @author Kenneth Leung
 */
public interface HttpEvent extends IoEvent, IoTrigger, Context {

  /**/
  public HttpCookie cookie(String name);

  /**/
  public Iterable<HttpCookie> cookies();

  /**/
  public XData body();

  /**/
  public Object msgGist();

  /**/
  public String localAddr();

  /**/
  public String localHost();

  /**/
  public int localPort();

  /**/
  public String remoteAddr();

  /**/
  public String remoteHost();

  /**/
  public int remotePort();

  /**/
  public String serverName();

  /**/
  public int serverPort();

  /**/
  public String scheme();

  /**/
  public boolean isSSL();

  /**/
  public void reply(HttpResult r);

  /**/
  public HttpSession session();

  /**
   */
  public boolean checkSession();

}



