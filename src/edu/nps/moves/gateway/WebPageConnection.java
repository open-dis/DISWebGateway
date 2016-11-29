/**
 * Lifted from http://amilamanoj.blogspot.com/2013/06/secure-websockets-with-jetty.html
 */

package edu.nps.moves.gateway;

/**
 * A class that corresponds to one connection to one web page client. This is created when
 * a client connects a websocket to us, one instance per connection. Typically there's one
 * websocket opened from the web page to the server.
 * 
 * This is the server side view of a connection.
 * 
 * @author DMcG
 */

import org.eclipse.jetty.websocket.api.RemoteEndpoint;
import org.eclipse.jetty.websocket.api.Session;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketClose;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketConnect;
import org.eclipse.jetty.websocket.api.annotations.OnWebSocketMessage;
import org.eclipse.jetty.websocket.api.annotations.WebSocket;

import org.json.*;
import java.nio.*;

import edu.nps.moves.disutil.*;
import edu.nps.moves.dis.*;

import java.io.IOException;
import java.util.IntSummaryStatistics;

@WebSocket
public class WebPageConnection implements DisEndpoint
{
    /** Every web socket connection has a unique number. This 
     * keeps a running count of the next.
     */
    private static int nextConnectionNumber = 0;
    
    /** The far side of the connection */
    private RemoteEndpoint remote;
    
    /** A unique connection number for each connection to a web page. */
    int connectionNumber = 0;
    
    /** Every connection can have an area of interest script attached
     * to it. This can do filtering on the server side based on arbitrary
     * criteria; it simply runs a Javacript program and provides a yes-or-no
     * answer about whether the server should forward it to the client.
     */
    AreaOfInterest aoim = null;
    
    /** The connection manager holds all the connections */
    ConnectionManager connectionManager = ConnectionManager.getConnectionManager();
   
    /** Collects stats on each connection--number of messages, bytes, latency, etc. */
    ConnectionStatistics connectionStatistics;
    

    /** Fired when the websocket from the client connects. Add it to the ConnectionManager's
     * list of connections.
     * 
     * @param session The session with the web client
     */
    @OnWebSocketConnect
    public void onConnect(Session session) 
    {
        System.out.println("WebSocket Opened");
        this.remote = session.getRemote();
        nextConnectionNumber++;
        this.connectionNumber = nextConnectionNumber;
        connectionStatistics = new ConnectionStatistics();
        
        // Add javascript AOIM to the connection
        
        String script = "load ('scripts/dis7.js')\n"
                    + "load ('scripts/BinaryUtils.js')\n"
                    + "var pduFactory = new dis.PduFactory();\n" 
                    + "function aoim(data) \n" 
                    +  "{  \n"
                    +  "  var pduBuffer = byteToUint8Array(data);\n"
                    + "   var pdu = pduFactory.createPdu(pduBuffer);\n "
                    + "   if(pdu.pduType !== 1) \n"
                    + "      return false;\n"
                    + "   \n"
                    + "  return true;\n"
                    + "};\n";
        
        System.out.println("Script is: " + script);
        this.aoim = new AreaOfInterest(script);
        
        ConnectionManager.getConnectionManager().addConnection(this);
    }

    /** Fired when the client sends a message. Tell the connection manager to repeat the 
     * message to all other clients, but do not loop it back to the sender.
     * 
     * @param message the (text) message sent from the client to the server, typicall in JSON format
     */
    @OnWebSocketMessage
    public void onMessage(String message) 
    {
        //System.out.println("Message from Client: " + message);
        connectionManager.repeatMessage(message, this);
        connectionStatistics.messageSent(message.getBytes());
       
    }
    
    /** Fired when a client sends a binary message. 
     *
     * @param buf binary data
     * @param offset offset into binary data (see interface)
     * @param length length of binary data (see interface)
     */
    @OnWebSocketMessage
    public void onBinaryMessage(byte buf[], int offset, int length)
    {
       //PduFactory factory = new PduFactory();
       //Pdu aPdu = factory.createPdu(buf);
       //System.out.println("Got web pdu of type " + aPdu.getClass().getName() );
        
       // The distribution of a message to all the clients is handled in a separate
       // thread. This decouples handling the incoming traffic from the distribution,
       // which can be slow. The idea is that we want to get in an out fast in
       // this method.
       connectionManager.enqueueBinaryMessage(buf, this);
       connectionStatistics.messageSent(buf);
    }

    /** Fired when the web socket closes. The client informs us, and we remove
     * the client from the list of active clients in ConnectionManager
     * 
     * @param statusCode reason for closure
     * @param reason string reason for closure
     */
    @OnWebSocketClose
    public void onClose(int statusCode, String reason) 
    {
            System.out.println("WebSocket Closed. Code:" + statusCode + " Reason:" + reason);
            ConnectionManager.getConnectionManager().removeConnection(this);
    }
    
    /**
     * Sends data in string format to the client. This is typically in
     * JSON format. This is usually called by the ConnectionManager.
     * 
     * @param message data to be sent
     */
    public void sendToClient(String message)
    {
        try
        {
            remote.sendString(message);
            connectionStatistics.messageSent(message.getBytes());
        }
        catch(IOException e)
        {
            System.out.println(e);
        }
    }
    
    /**
     * Sends data in binary format to the client. The end client is typically
     * another web page. This is called typically from ConnectionManager.
     * 
     * @param buf typically a IEEE-1278.1 DIS packet
     */
    public void sendBinaryToClient(byte[] buf)
    {
        // Check if this PDU passes the critera of AOIM for this
        // connnection
        if(aoim != null && Configuration.ENABLE_AOIM)
        {
            //System.out.println("Performing AOIM test");
            if( !(aoim.pduPassesAOIM(buf)))
            {
                //System.out.println("Failed AOIM test");
                return;
            }
               //System.out.println("Passed AOIM test");
        }
        
        try
        {
            remote.sendBytes(ByteBuffer.wrap(buf));
            remote.flush();
            connectionStatistics.messageSent(buf);
            //System.out.println("Sent to connection " + connectionNumber);
        }
        catch(IOException e)
        {
            System.out.println("In connection " + connectionNumber);
            System.out.println(e);
            e.printStackTrace();

        }
    }
    
    public ConnectionStatistics getConnectionStatistics()
    {
        return connectionStatistics;
    }

}
