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

package com.zotohlab.wflow;

/**
 * A logical group - sequence of connected activities.
 *
 * @author kenl
 *
 */
public class Block extends Composite {

  public static Block apply(Activity a) {
    return new Block(a);
  }

  public Block(String name, Activity a) {
    this(name);
    add(a);
  }
  
  public Block(Activity a) {
    this("",a);
  }

  public Block(String name) {
    super(name);
  }
  
  public Block() {
    this("");
  }

  public Activity chainMany(Activity... acts) {
    for (Activity a: acts) {
      add(a);
    }
    return this;
  }

  public Activity chain(Activity a) {
    add(a);
    return this;
  }

  public FlowNode reifyNode(FlowNode cur) {
    return new BlockNode(cur,this);
  }

}


