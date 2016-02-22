
package edu.nps.moves.gateway;

import edu.nps.moves.gateway.ConnectionManager;

public class MessageDistributor implements Runnable
    {
        static int nextId = 0;
        int id;
        
        ConnectionManager connectionManager = null;
        
        public MessageDistributor(ConnectionManager connectionManager)
        {
            this.connectionManager = connectionManager;
            this.id = nextId;
            nextId++;
        }
        
        public void run()
        {
            
            while(true)
            {
                try
                {
                    // Blocks until there is a message in the queue to retrieve
                    ConnectionManager.BinaryMessageAndSender msg = connectionManager.getNextMessage();
                    connectionManager.repeatBinaryMessage(msg.data, msg.sender);
                    //System.out.println("Sent from " + id);
                }
                catch(Exception e)
                {
                    System.out.println(e);
                    e.printStackTrace();
                }
            }
        }
        
    }
