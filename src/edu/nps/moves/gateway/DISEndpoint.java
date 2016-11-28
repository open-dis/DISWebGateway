
package edu.nps.moves.gateway;

import java.util.IntSummaryStatistics;
/**
 * interface for things that can read or send binary or JSON format DIS packets. This
 * may be a web page connected via a web socket or, on the server side, a 
 * native socket that reads and writes bcast or mcast DIS.
 * 
 * @author DMcG
 */
public interface DisEndpoint 
{
    /** Send binary format DIS to an endpoint. 
     * @param buf binary format message, typcially iee1278.1 DIS
     */
   public void sendBinaryToClient(byte[] buf);
   
   /** Send JSON format DIS to a client 
    * @param aMessage JSON format message
    */
   public void sendToClient(String aMessage);
   
   /**
    * Returns the connection statistics for this connection. Includes
    * number of messages sent and potentially other info, such as 
    * observed latency.
    * 
    * @return Object that encapsulates connection stats.
    */
   public ConnectionStatistics getConnectionStatistics();

}
