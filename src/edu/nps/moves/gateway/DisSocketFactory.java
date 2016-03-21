
package edu.nps.moves.gateway;

import java.net.*;
import java.util.*;

/**
 * Creates a multicast socket. Uses SO_REUSE_ADDR, so the socket can be
 * opened even if another program is already listening on that port.
 * @author DMcG
 */
public class DisSocketFactory 
{
    /**
     * Create a UDP socket. If multicast group is not null, join that multicast group.
     * The so_reuse_addr socket option to set on, as is the bcast socket option.
     * 
     * @param port The socket port
     * @param multicastGroup the multicast group. Join it if not null
     * @return A multicast socket with so_reuse_addr on, and bcast socket option on, bound to the given port
     */
    public static MulticastSocket getDisSocket(int port, String multicastGroup)
    {
        MulticastSocket socket = null;
        InetAddress group;
        
       try
       {
           // Some fancy footwork to allow us to open a UDP socket even if
           // anther program has already opened a socket on that port. Opens
           // on the wildcard address, ie all configured interfaces--both wired
           // and wireless, typically.
           
           // We want do do this because often we have another application on this
           // host generating traffic, or listening for traffic, also on the default 
           // DIS port. Should also bind to 0.0.0.0, inaddr_any, ie all IPs.
           
           socket = new MulticastSocket(null); // Not bound yet
           socket.setReuseAddress(true);   //so_reuse_addr
           socket.setBroadcast(true);
           socket.bind(new InetSocketAddress(port));  // Bind it to a port, wildcard address (0.0.0.0)
            
           // If a mcast group is supplied, join that.
           if(multicastGroup != null)
           {
               group = InetAddress.getByName(multicastGroup);
               if(group.isMulticastAddress())
               {
                   socket.joinGroup(InetAddress.getByName(multicastGroup));
               }
           }
           
       }
       catch(Exception e)
       {
           System.out.println(e);
           e.printStackTrace();
       }
       
       return socket;
    }
}
