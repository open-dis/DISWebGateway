
package edu.nps.moves.gateway;

import redis.clients.jedis.*;
import java.io.*;
import java.util.concurrent.*;
import edu.nps.moves.dis.*;
import java.util.Arrays;
import java.util.IntSummaryStatistics;

/**
 * Endpoint that forwards to a Redis server. This allows DIS
 * messages to be passed among a back-end cluster of Websocket
 * servers, instead of a single Websocket server.<p>
 * 
 * The documentation on Jedis is mighty thin. It seems that publish (?)
 * and subscribe actions want to be in their own threads, near as
 * I can figure.<p>
 * 
 * Since we want only a single DisEndpoint communicating with the
 * redis server, we use a singleton pattern. Use getInstance() to
 * retrieve the single, shared object.<p>
 * 
 * To prevent loops, in which we send a DIS PDU to the redis server
 * on the publish channel, get it back on the subscribe channel, and
 * then get it looping, lost dutchman style, we prepend a small bit
 * of data at the start of the binary PDU with a randomly generated,
 * very unlikely to be duplicated number. We then discard any messages
 * we receive from the redis server that have our ID.
 * 
 * @author DMcG
 */
public class DisRedis implements DISEndpoint
{
    // Should be moved to properties file
    
    /** Host name of Redis server */
    public static final String REDIS_SERVER="localhost";
    
    /** Port Redis server is listening on */
    public static final int    REDIS_PORT= 6379;
    
    /* PubSub channel name */
    public static final String REDIS_PUBSUB_CHANNEL = "DIS";
    
    /** We typically want only one instance of a DisEndpoint talking
     * to the Redis server. To enforce this we use the singleton pattern
     */
    private static DisRedis instance = null;
    
    /** Pool of connections to the Jedis server. Retrieve a connection
     * with getResource(), and return it to the pool with getResource()
     * and return it to the pool with returnObject(). It turns out we
     * just use a couple connections and don't really worry about a
     * dynamic pool.
     */
    private static JedisPool pool = null;
    
    /** Handles messages received from jedis pubsub server. The
     * pubSub object here handles binary messages. There is a parallel
     * version that handles text messages, eg JSON. It would be better
     * to implement this as an anonymous class.
     */
    private PublishSubscribe pubSub; // binary version
    
    /** Connection to Redis server for publish */
    private  static Jedis publishJedisBinaryConnection = null;
    
    /** Connection to redis server for subscribed message receipt */
    private  static Jedis subscribeJedisBinaryConnection = null;
    
    /** Summary stats for messages sent */
    IntSummaryStatistics messagesSent;
    
    /** Summary stats for messages received */
    IntSummaryStatistics messagesReceived;
    
    /** Unique, randomly-generated ID for this websocket server in 
     * the pool. 
     */
    int websocketServerID;
    
    /**
     * Get the single shared instance of DisRedis
     * 
     * @return shared DisRedis instance
     */
    public static synchronized DisRedis getInstance()
    {
        if(instance == null)
        {
            instance = new DisRedis();
        }
        
        return instance;
    }
    
    /**
     * Private constructor. Use getInstance() to retrieve the single,
     * shared DisRedis object.
     */
    private DisRedis()
    { 
        try  
        {
            this.messagesSent = new IntSummaryStatistics();
            this.messagesReceived = new IntSummaryStatistics();
            
            websocketServerID = (int)(Math.random() * Integer.MAX_VALUE);
            JedisPoolConfig cfg = new JedisPoolConfig();
            cfg.setMaxTotal(5);
            
            // REDIS_SERVER and REDIS_PORT should be moved to a config file
            pool = new JedisPool(cfg, Configuration.REDIS_SERVER, Configuration.REDIS_PORT);
            
            // Get two connections, one for publishing and one for
            // receiving messages over the subscribed channel.
            // (can these be combined?)
            publishJedisBinaryConnection = pool.getResource();
            subscribeJedisBinaryConnection = pool.getResource();
            
            Jedis testJedis = pool.getResource();
  
            String retVal = testJedis.set("foo", "bar");
            String foobar = testJedis.get("foo");
            if( retVal.equals("OK") )
                System.out.println("Sucessfully connected to Redis server " + REDIS_SERVER + " on port " + REDIS_PORT);
              else
              {
                    System.out.println("ERROR: unable to connect to Redis server " + REDIS_SERVER + " on port " + REDIS_PORT);
                    return;
              }
            
            // Start threads to handle publish and subscribe, respectively.
            // (Really necessary? Badly documented in Jedis, and while this
            // works, there may be a better way to do it. )
            this.setupSubscribe(subscribeJedisBinaryConnection);
            //this.setupPublish(publishJedisBinaryConnection);
        }
        catch(Exception e)
        {
            System.out.println("Failed to start connection to Redis server " + e);
            e.printStackTrace();
        }
  };
    
    public int getWebsocketServerID()
    {
        return websocketServerID;
    }
    /**
     * Start thread to handle publishing to Redis server
     * 
     * @param publishJedis 
     */
    private void setupPublish(Jedis publishJedis)
    {
        Thread publishTextThread = new Thread(new Runnable()
        {
            @Override
            public void run()
            {
                try
                {
                    System.out.println("connecting to jedis server in setupPublish");
                    
                    DisRedis.publishJedisBinaryConnection.publish(Configuration.REDIS_PUB_SUB_CHANNEL.getBytes(), new EntityStatePdu().marshal());
                    System.out.println("Published binary message");
                    System.out.println("Exiting setupPublish");
                }
                catch(Exception e)
                {
                    System.out.println("Problem publishing: " + e);
                }
            }
        });
        publishTextThread.start();
    };
    
    /** Set up a subscription to the channel name specified.
     * 
     * @param subscribeJedis 
     */
    private void setupSubscribe(Jedis subscribeJedis)
    {
        new Thread(new Runnable()
        {
            @Override
            public void run()
            {               
                // PubSub object with onMessage() methods to trap notifications
                // that we have received a message from the server
               DisRedis.this.pubSub = new PublishSubscribe();
               DisRedis.subscribeJedisBinaryConnection.subscribe(pubSub, REDIS_PUBSUB_CHANNEL.getBytes());
            }
        }).start();
    }
    
    
   /** Send binary format DIS to an endpoint.
    * @param buf binary data
    */
    @Override
   public void sendBinaryToClient(byte[] buf)
   {
     //System.out.println("Forwarding binary DIS to Redis server") ;
     
     // Annoying footwork. We prepend the websocketServerID (a randomly
     // generated ID for this particular web server) to the DIS PDU
     // byte array. We use this to detect loops when we receive a 
     // message from the redis server; if we sent it, we discard it.
     // I don't think this has a huge effect on performance, but maybe
     // you could instead bitmask in the values to the array and avoid
     // a bunch of object allocations. 
     try
     {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        DataOutputStream dos = new DataOutputStream(baos);
        dos.writeInt(websocketServerID);
        
        // create an array big enough to hold both the original message
        // and the prepended tag
        byte[] tag = baos.toByteArray();
        byte[] full = new byte[tag.length + buf.length];
        
        // concatenate the array with the tag and the array with the
        // original message
        System.arraycopy(tag, 0, full, 0, tag.length);
        System.arraycopy(buf, 0, full, tag.length, buf.length);
        
        // Publish it.
        DisRedis.publishJedisBinaryConnection.publish(DisRedis.REDIS_PUBSUB_CHANNEL.getBytes(), full);
        messagesSent.accept(1);
     }
     catch(Exception e)
     {
         System.out.println("Problem sending DIS message to Redis server:" + e);
     }
     
   }
   
   /** Send JSON format DIS to a client 
    * @param aMessage string message, typically JSON
    */
   @Override
   public void sendToClient(String aMessage)
   {
       //System.out.println("Forwarding DIS message to Redis server");
       DisRedis.publishJedisBinaryConnection.publish(REDIS_PUBSUB_CHANNEL, "WockaWockaWocka");
       messagesSent.accept(1);
   }
   
     /** Get the summary statistics object for this connection
    *  for the number of  messages (binary and json) sent. IntSummaryStatistics
    *  is a JDK8 class.
    * 
    * @retrun IntSummaryStatistics summary stats for messages sent
    */
   public IntSummaryStatistics getMessagesSentSummaryStatistics()
   {
       return messagesSent;
   }
   
   /** Get the summary statistics object for this connection
    *  for the number of binary messages (binary and json) received. 
    * IntSummaryStatistics is a JDK8 class.
    * 
    * @retrun IntSummaryStatistics summary stats for messages sent
    */
    public IntSummaryStatistics getMessagesReceivedSummaryStatistics()
    {
        return messagesReceived;
    }
   
   
}

