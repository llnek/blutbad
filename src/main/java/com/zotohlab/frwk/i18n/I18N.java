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

package com.zotohlab.frwk.i18n;

import java.util.ResourceBundle;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * @author kenl
 */
public enum I18N {
;

  private static Logger _log= LoggerFactory.getLogger(I18N.class);
  public static Logger tlog() { return _log; }

  private static ResourceBundle _base;

  public static ResourceBundle getBase() {
    return _base;
  }

  public static void setBase(ResourceBundle b) {
    _base=b;
  }

}

