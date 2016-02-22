
package edu.nps.moves.gateway;

import java.util.IntSummaryStatistics;
/**
 * interface for things that can read or send binary or JSON format DIS packets. This
 * may be a web page connected via a web socket or, on the server side, a 
 * native socket that reads and writes bcast or mcast DIS.
 * 
 * @author DMcG
 */
public interface DISEndpoint 
{
    /** Send binary format DIS to an endpoint. */
   public void sendBinaryToClient(byte[] buf);
   
   /** Send JSON format DIS to a client */
   public void sendToClient(String aMessage);
   
   /** Get the summary statistics object for this connection
    *  for the number of  messages (binary and json) sent. IntSummaryStatistics
    *  is a JDK8 class.
    * 
    * @retrun IntSummaryStatistics summary stats for messages sent
    */
   public IntSummaryStatistics getMessagesSentSummaryStatistics();
   
   /** Get the summary statistics object for this connection
    *  for the number of binary messages (binary and json) received. 
    * IntSummaryStatistics is a JDK8 class.
    * 
    * @retrun IntSummaryStatistics summary stats for messages sent
    */
    public IntSummaryStatistics getMessagesReceivedSummaryStatistics();

}
