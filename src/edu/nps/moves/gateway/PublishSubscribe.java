/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */

package edu.nps.moves.gateway;

import redis.clients.jedis.*; 
import edu.nps.moves.dis.*;
import java.io.*;

/**
 *
 * @author DMcG
 */
public class PublishSubscribe extends BinaryJedisPubSub
{
  /**
    * Receive a message from a channel. See superclass
    * @param channel channel
    * @param message message
    */
   @Override
   public void onMessage(byte[] channel, byte[] message) 
   {
       //System.out.println("onMessage");
       
       // Check the prepended tag to see if we sent it
       ByteArrayInputStream bais = new ByteArrayInputStream(message);
       DataInputStream dis = new DataInputStream(bais);
       
       // Check to see if the tag, which is prepended to the binary
       // message, is equal to our tag. If it is, we sent it to the
       // redis server, and we should discard it.
       
       try
       {
           // Check for the tag; if it's ours, discard and continue
        int tag = dis.readInt();
        if(tag == DisRedis.getInstance().getWebsocketServerID())
        {
            //System.out.println("Discarding message from us from redis server");
            return;
        }
        
        
        // It's from another websocket server. Forward it to all our clients
        byte[] data = new byte[message.length - 4];
        System.arraycopy(message, 4, data, 0, message.length - 4);
        
        ConnectionManager.getConnectionManager().enqueueBinaryMessage(data, DisRedis.getInstance());
       }
       catch(Exception e)
       {
           System.out.println(e);
       }
  }

   /**
    * See superclass.
    * @param channel channel
    * @param subscribedChannels subscribed channels
    */
   @Override
   public void onSubscribe(byte[] channel, int subscribedChannels) 
   {
       System.out.println("onSubscribe");
   }

   /**
    * See superclass
    * @param channel channel
    * @param subscribedChannels subscribed channels
    */
   @Override
    public void onUnsubscribe(byte[] channel, int subscribedChannels) 
    {
        System.out.println("onUnsubscribe");
    }

    /**
     * See superclass
     * @param pattern pattern
     * @param subscribedChannels subscribed channels
     */
    @Override
    public void onPSubscribe(byte[] pattern, int subscribedChannels) 
    {
        System.out.println("onPSubscribe");
    }

    /**
     * See superclass
     * @param pattern pattern
     * @param subscribedChannels subscribed channels
     */
    @Override
   public void onPUnsubscribe(byte[] pattern, int subscribedChannels) 
   {
       System.out.println("onPUnsbuscribe");
   }

   /**
    * see superclass
    * @param pattern pattern
    * @param channel channel
    * @param message message
    */
   @Override
   public void onPMessage(byte[] pattern, byte[] channel,
            byte[] message) 
   {
       System.out.println("onPMessage");
   }

}
