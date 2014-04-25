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

package com.zotohlabs.wflow;

import java.util.ArrayList;
import java.util.List;
import java.util.ListIterator;

/**
 * @author kenl
 *
 */
public abstract class Composite extends Activity {

  private List<Activity> _children= new ArrayList<Activity>();

  public int size() { return _children.size(); }

  protected void add(Activity a) {
    _children.add(a);
    onAdd(a);
  }

  protected void onAdd(Activity a) {}

  public ListIterator<Activity> listChildren() { return _children.listIterator(); }

  public void realize(FlowPoint fp) {
    CompositePoint p= (CompositePoint ) fp;
    p.reifyInner( listChildren() );
  }

}

