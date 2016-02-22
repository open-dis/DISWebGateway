
package edu.nps.moves.gateway;


import edu.nps.moves.dis.*;
import edu.nps.moves.disutil.*;

import java.net.*;
import java.util.*;
/**
 * Reads and writes DIS from a native socket on the local network. This 
 * implements the DISEndpoint interface, which allows it to interoperate
 * with the ConnectionManager.
 * 
 * @author DMcG
 */
public class DisNative implements Runnable, DISEndpoint
{
    public static final int PORT = 3000;
    
    /** Max size of UDP DIS packets to receive. May need to be bigger for bundled PDUs,
     * in which multiple PDUs are placed in one UDP packet. Typical max PDU size
     * for a single PDU is under the ethernet MTU, typically about 1500 bytes, but
     * maybe not if there are a lot of articulation parameters. Senders can also
     * bundle PDUs; several PDUs may be placed in one datagram. Those need to be
     * decoded with the getPdusFromBundle() method in PduFactory.
     */
    private static final int MAX_UDP_PACKET_SIZE = 8 * 1024;
    
    /** Summary stats for messages sent */
    IntSummaryStatistics messagesSent;
    
    /** Summary stats for messages received */
    IntSummaryStatistics messagesReceived;
    
    /** socket can be used for either bcast or multicast */
    private MulticastSocket multicastSocket;
    
    /** If reading from multicast, use this address */
    private InetAddress multicastAddress;
    
    /** Port socket listens on for DIS traffic */
    private int  port;
    
    /** Used for performance instrumentation */
    private int messageCount = 0;
    
    /** Used for performance instrumentation */
    private Date startTime = new Date();
    
    /** All bcast addresses for this host. Discovered at runtime. */
    private Set broadcastAddresses = this.getBroadcastAddresses();
    
    /** Converts IEEE binary DIS to a Java DIS PDU object */
    private PduFactory pduFactory = new PduFactory();
    
    /** Unique ID placed in the padding of PDUs sent. This is a cheat, intended
     * to prevent routing loops. We want the gateway ID to be non-zero.
     */
    private short gatewayID = (short)((Math.random() * 254) + 1);
    
    /** 
     * Constructor
     * @param socket socket we read and write on. You should create this socket with the DisSocketFactory object
     * @param multicastAddress If null we read & write bcast. Otherwise mcast on this address
     */
    public DisNative(MulticastSocket socket, InetAddress multicastAddress, int port)
    {
        this.multicastSocket = socket;
        this.multicastAddress = multicastAddress;
        this.port = port;
        this.messagesSent = new IntSummaryStatistics();
        this.messagesReceived = new IntSummaryStatistics();
    }
    
    /**
     * Entry point for thread. Endlessly loop, reading UDP DIS packets.
     */
    @Override
    public void run()
    {
        // Get the manager for all things that can read and write DIS, including
        // web pages connected via a web socket and, like this object, a native
        // socket reading and writing DIS from the local network.
        ConnectionManager connectionManager = ConnectionManager.getConnectionManager();
        
        // Get all the bcast addresses for this host
        this.getBroadcastAddresses();
        
        // Performance monitoring
        Hashtable entities = new Hashtable();
        
        // Read DIS from the local network. Send native DIS packets to all web
        // pages that are connected via web sockets.
        while(true)
        {
            try
            {
                byte[] buffer = new byte[ MAX_UDP_PACKET_SIZE];
                DatagramPacket packet = new DatagramPacket( buffer, buffer.length );
                multicastSocket.receive(packet);
                
                //System.out.println("got DIS pdu native");
                
                // Turn the DIS binary into a Java object. This is optional,
                // but useful; we can perhaps do filtering based on the location,
                // and right now we only send ESPDU's to web clients. In contrast
                // to JSON, this is pretty cheap in the cosmic scheme of things.
                // Meaning it's not all that effcient, but get a life.
                
                // We can handle bundled PDUs here, in which case we will need to
                // loop through the list of several PDUs received in a single datagram.
                //List<Pdu> pduBundleContents = pduFactory.getPdusFromBundle(buffer);
                //System.out.println("PDU Bundle contains " + pduBundleContents.size());
                
                Pdu aPdu = pduFactory.createPdu(packet.getData());
              
                // Can't interepret the packet? It's probably not DIS, or it's not
                // a supported PDU type, so don't forward it to the web clients.
                /*
                if( pduBundleContents == null || pduBundleContents.size() == 0 )
                {
                    continue;
                }
                */
                
                //for(Pdu aPdu : pduBundleContents )
                //{
                    // one problem we can have is routing loops. For example, a web
                    // client sends a PDU. We receive it and send it out on the native
                    // interface. Via the rules of bcast and mcast, we also _receive_
                    // that PDU. We may accidentally send another copy to all the
                    // web clients. So we set the padding field to contain a value,
                    // and discard the pdu if we're the one that sent it.
                    if(aPdu.getPadding() == gatewayID)
                    {
                        //System.out.println("Discarding looped packet, id=" + gatewayID);
                        continue;
                    }

                    /*
                    messageCount++;
                    if(messageCount % 10000 == 0)
                    {
                        Date endTime = new Date();
                        long elapsedTime = endTime.getTime() - startTime.getTime();
                        System.out.println("Elapsed time = " + elapsedTime);
                        System.out.println("Packets per second received, 10K packets:" + 10000.0 / (elapsedTime / 1000.0));
                        System.out.println("Entity world count: " + entities.keySet().size());
                        messageCount = 0;
                        startTime = endTime;
                    }
                    */

                    if(aPdu instanceof EntityStatePdu)
                    {
                        
                        EntityStatePdu espdu = (EntityStatePdu)aPdu;
                        //System.out.println("Got espdu for " + new String(espdu.getMarking().getCharacters()));
                        if(entities.get(espdu.getEntityID()) == null)
                        {
                            entities.put(espdu.getEntityID(), aPdu);
                        }
                    }


                    // We know this is an PDU. Forward out the PDU to all clients,
                    // including any web pages. Limit the size of the binary data
                    // to only the length of the data received.
                    byte trimmedData[] = new byte[packet.getLength()];
                    System.arraycopy(buffer, 0, trimmedData, 0, packet.getLength());
                    connectionManager.enqueueBinaryMessage(trimmedData, this);
                    messagesReceived.accept(1);

                    //System.out.println("Got PDU");
                    // We can also convert this to JSON if we like.
                    //JSONObject obj = new JSONObject(aPdu);
                    //System.out.println(obj.toString());
                }
            //}
            catch(Exception e)
            {
                System.out.println(e);
                e.printStackTrace();
            }
        }
    }
   
    /**
     * Sending strings to the local network is probably a bad idea. For now
     * we simply no-op this because it doesn't make sense to send a non-standard
     * string to the network.
     * @param aMessage 
     */
    @Override
   public void sendToClient(String aMessage)
   {
   }

   /**
    * Send native DIS to the local native network. Typically we receive DIS
    * from some web page, and send it to the local network.
    * @param buf DIS data
    */
   @Override
   public void sendBinaryToClient(byte[] buf)
   {
       // Send out on all bcast addresses on all interfaces, or send out
       // in mcast format.
       
       //System.out.println("IN native, sendBinaryToClient");
       try
       {
          Pdu aPdu = pduFactory.createPdu(buf);
          
          // Not DIS, or at least not decodable by open-dis as DIS? Drop it.
          if(aPdu == null)
              return;
          
          // Helps prevent routing loops. We stick a number in an unused portion
          // of the PDU, so when we read it back we know we sent it. This is kind
          // of bogus, but we're combining bridging and routing in one layer, so
          // there's no good way around it.
          aPdu.setPadding(gatewayID);
          byte data[] = aPdu.marshalWithDisAbsoluteTimestamp();
          
          // Send to multicast if we have a multicast address set
          if(multicastAddress != null)
          {
            DatagramPacket packet = new DatagramPacket(data, data.length, multicastAddress, port);
            multicastSocket.send(packet);
            messagesSent.accept(1);
          }
          else // send bcast
          {
              Iterator it = broadcastAddresses.iterator();
              while(it.hasNext())
              {
                  InetAddress aBcast = (InetAddress)it.next();
                  //System.out.println("Sending to bcast address:" + aBcast);
                  DatagramPacket packet = new DatagramPacket(data, data.length, aBcast, port);
                  multicastSocket.send(packet);
                  messagesSent.accept(1);
              }
            
          }
       }
       catch(Exception e)
       {
          System.out.println(e); 
          e.printStackTrace();
       }
       
   }
   
   /**
    * A number of sites get all snippy about using 255.255.255.255 for a bcast
    * address; it trips their security software and they kick you off their 
    * network. (Comcast, NPS.) Besides, the 255.255.255.255 address has been
    * deprecated for a long time. This method determines the bcast address for all
    * connected interfaces, based on the IP and subnet mask. If you have
    * a dual-homed host it will return a bcast address for both. If you have
    * some VMs running on your host this will pick up the addresses for those
    * as well--eg running VMWare on your laptop with a local IP this will
    * also pick up a 192.168 address assigned to the VM by the host OS.
    * 
    * @return set of all bcast addresses
    */
   Set getBroadcastAddresses()
   {
       // All the bcast inet addresses
       Set<InetAddress> bcastAddresses = new HashSet<InetAddress>();
       // An interface (eg, eth0, eth1) is a port, such as an ethernet
       // port, that may have zero or more IPv4 addresses assigned to 
       // it. For example eth0 may have 10.1.1.20 and 192.168.1.2 assigned to it.
       Enumeration interfaces;
       
       try
       {
           // 0.0.0.0 is an alias for "any IPv4 address", sometimes known as
           // IPADDR_ANY. There are some other meanings; see http://en.wikipedia.org/wiki/0.0.0.0.
           // Basically, if we get this returned as a broadcast address, we know
           // something is borked, and shouldn't use it.
           InetAddress INADDR_ANY = InetAddress.getByName("0.0.0.0");
           
           // All interfaces on this host, eg eth0, eth1, etc. We may have multiple
           // IPs on a single interface.
           interfaces = NetworkInterface.getNetworkInterfaces();
           
           // Loop through all the interfaces
           while(interfaces.hasMoreElements())
           {
               NetworkInterface anInterface = (NetworkInterface)interfaces.nextElement();
               
               // Is the interface up?
               if(anInterface.isUp())
               {
                   Iterator it = anInterface.getInterfaceAddresses().iterator();
                   
                   // Loop through all the IP addresses on this interface
                   while(it.hasNext())
                   {
                       InterfaceAddress anAddress = (InterfaceAddress)it.next();
                       if((anAddress == null || anAddress.getAddress().isLinkLocalAddress()))
                           continue;
                       
                       // IPv6 IP addresses should return null for a bcast address;
                       // no bcast in IPv6.
                       InetAddress abcast = anAddress.getBroadcast();
                       
                       // In some odd windows cases we seem to get back "0.0.0.0"
                       // as a bcast address, which causes problems if we try
                       // to use it. Punt if that's the case.
                       if( abcast == null || abcast == INADDR_ANY )
                           continue;
                       
                       // Add it to the list of bcast addresses
                       bcastAddresses.add(abcast);
                       //System.out.println("Interface: " + anInterface + " Address: " + anAddress + " Bcast address: " + abcast);
                   }
               }
           }
           
       }
       catch(Exception e)
       {
           e.printStackTrace();
           System.out.println(e);
       }
       
       return bcastAddresses;   
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
