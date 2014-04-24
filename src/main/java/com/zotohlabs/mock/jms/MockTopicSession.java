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

package com.zotohlabs.mock.jms;

import javax.jms.*;
import static com.zotohlabs.mock.jms.MockUtils.*;

import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;


/**
 * @author kenl
 *
 */
public class MockTopicSession implements TopicSession {
  
  private Map<String,TopicSubscriber> _subs = new HashMap<String,TopicSubscriber>();
  private volatile boolean _active = false;

  private MockTopicConnection _conn;
  private boolean _tx;
  private int _ack;

  public MockTopicSession(MockTopicConnection c, boolean tx, int a) {
    _conn=c;
    _tx=tx;
    _ack=a;
  }

  @Override
  public BytesMessage createBytesMessage() throws JMSException {
    return null;
  }

  @Override
  public MapMessage createMapMessage() throws JMSException {
    return null;
  }

  @Override
  public Message createMessage() throws JMSException {
    return null;
  }

  @Override
  public ObjectMessage createObjectMessage() throws JMSException {
    return null;
  }

  @Override
  public ObjectMessage createObjectMessage(Serializable serializable) throws JMSException {
    return null;
  }

  @Override
  public StreamMessage createStreamMessage() throws JMSException {
    return null;
  }

  @Override
  public TextMessage createTextMessage() throws JMSException {
    return createTextMessage("");
  }

  @Override
  public TextMessage createTextMessage(String s) throws JMSException {
    return new MockTextMessage(s);
  }

  @Override
  public boolean getTransacted() throws JMSException {
    return false;
  }

  @Override
  public int getAcknowledgeMode() throws JMSException {
    return 0;
  }

  @Override
  public void commit() throws JMSException {

  }

  @Override
  public void rollback() throws JMSException {

  }

  @Override
  public void close() throws JMSException {
    _active=false;
  }

  @Override
  public void recover() throws JMSException {

  }

  @Override
  public MessageListener getMessageListener() throws JMSException {
    return null;
  }

  @Override
  public void setMessageListener(MessageListener messageListener) throws JMSException {

  }

  @Override
  public void run() {
    _active=true;
    Thread t= new Thread(new Runnable() {
      public void run() {
        try {
          Thread.sleep(3000);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        while (_active && _conn.isActive()) try {
          trigger();
          Thread.sleep(3000);
        } catch (Throwable e) {}
        _active=false;
      }
    }) ;
    t.setDaemon(true);
    t.start();
  }

  private void trigger() throws Exception {
    Message m= createTextMessage(makeNewTextMsg_x() );
    m.setJMSType("Mock-Topic-Type");

    for (TopicSubscriber r : _subs.values()) {
      MessageListener ml=r.getMessageListener();
      if (ml != null) {
        ml.onMessage(m);
      }
    }
  }

  @Override
  public MessageProducer createProducer(Destination destination) throws JMSException {
    return null;
  }

  @Override
  public MessageConsumer createConsumer(Destination destination) throws JMSException {
    return null;
  }

  @Override
  public MessageConsumer createConsumer(Destination destination, String s) throws JMSException {
    return null;
  }

  @Override
  public MessageConsumer createConsumer(Destination destination, String s, boolean b) throws JMSException {
    return null;
  }

  @Override
  public Queue createQueue(String s) throws JMSException {
    return null;
  }

  @Override
  public Topic createTopic(String s) throws JMSException {
    return null;
  }

  @Override
  public TopicSubscriber createSubscriber(Topic t) throws JMSException {
    TopicSubscriber s= new MockTopicSubscriber(t,"");
    _subs.put( t.getTopicName() ,s);
    return s;
  }

  @Override
  public TopicSubscriber createSubscriber(Topic topic, String s, boolean b) throws JMSException {
    return null;
  }

  @Override
  public TopicSubscriber createDurableSubscriber(Topic t, String n) throws JMSException {
    TopicSubscriber sub= new MockTopicSubscriber(t, n);
    _subs.put(t.getTopicName() ,sub);
    return sub;
  }

  @Override
  public TopicSubscriber createDurableSubscriber(Topic topic, String s, String s2, boolean b) throws JMSException {
    return null;
  }

  @Override
  public QueueBrowser createBrowser(Queue queue) throws JMSException {
    return null;
  }

  @Override
  public QueueBrowser createBrowser(Queue queue, String s) throws JMSException {
    return null;
  }

  @Override
  public TemporaryQueue createTemporaryQueue() throws JMSException {
    return null;
  }

  @Override
  public TopicPublisher createPublisher(Topic topic) throws JMSException {
    return null;
  }

  @Override
  public TemporaryTopic createTemporaryTopic() throws JMSException {
    return null;
  }

  @Override
  public void unsubscribe(String s) throws JMSException {

  }
}
