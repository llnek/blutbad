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

package com.zotohlab.frwk.dbio;

import java.util.Iterator;

/**
 * @author kenl
 */
public interface SQLr {

  public Iterator<?> findSome(Object modeldef, Object  filters, Object  extras);

  public Iterator<?> findSome(Object modeldef, Object  filters);

  public Iterator<?> findAll(Object modeldef, Object extras);

  public Iterator<?> findAll(Object modeldef);

  public Object findOne(Object modeldef, Object  filters);

  public Object update(Object obj);
  public Object delete(Object obj);
  public Object insert(Object obj);

  public Iterator<?> select(Object modeldef, String sql, Iterator<?> params);
  public Iterator<?> select(String sql, Iterator<?> params);

  public Object execWithOutput(String sql, Iterator<?> params);
  public Object exec(String sql, Iterator<?> params);

  public int countAll(Object modeldef);

  public void purge(Object modeldef);

  public Object  metas();

}




