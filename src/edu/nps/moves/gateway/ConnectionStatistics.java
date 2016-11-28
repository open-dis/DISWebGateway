
package edu.nps.moves.gateway;

import java.util.*;

/**
 * Collects various statistics on the performance of a network connection.
 * Includes the number of messages sent and received, optionally latency,
 * and so on.
 * 
 * @author DMcG
 */
public class ConnectionStatistics 
{
    /** Time at which this connection was created */
    private Date creationTime;
    
    /** Count of messages sent */
    private IntSummaryStatistics messagesSentData;
    
    /** Count of messages received */
    private IntSummaryStatistics messagesReceivedData;
    
    /** Latency observations */
    private DoubleSummaryStatistics latencyObservationsData;
    
    /** Number of bytes sent */
    private IntSummaryStatistics bytesSentData;
    
    /** Number of bytes received */
    private IntSummaryStatistics bytesReceivedData;
    
    // Stats by PDU type?
    
    public ConnectionStatistics()
    {
        messagesSentData = new IntSummaryStatistics();
        bytesSentData = new IntSummaryStatistics();
        messagesReceivedData = new IntSummaryStatistics();
        bytesReceivedData = new IntSummaryStatistics();
        creationTime = new Date();
        latencyObservationsData = new DoubleSummaryStatistics();
        
    }
    public void messageSent(byte[] data)
    {
        messagesSentData.accept(1);
        bytesSentData.accept(data.length);
    }
    
    public void messageReceived(byte[] data)
    {
        messagesReceivedData.accept(1);
        bytesReceivedData.accept(data.length);
    }
    
    public void latencyObservation(double howLong)
    {
        this.getLatencyObservationsData().accept(howLong);
    }

    /**
     * @return the messagesSentData
     */
    public IntSummaryStatistics getMessagesSentData() {
        return messagesSentData;
    }

    /**
     * @return the messagesReceived
     */
    public IntSummaryStatistics getMessagesReceivedData() {
        return messagesReceivedData;
    }

    /**
     * @return the latencyObservations
     */
    public DoubleSummaryStatistics getLatencyObservationsData() {
        return latencyObservationsData;
    }

    /**
     * @return the bytesSent
     */
    public IntSummaryStatistics getBytesSentData() {
        return bytesSentData;
    }

    /**
     * @return the number of bytesReceived
     */
    public IntSummaryStatistics getBytesReceivedData() {
        return bytesReceivedData;
    }
    
    /** Returns the time at which this connection was created
     * 
     * @return time of creation
     */
    public Date getCreationTime()
    {
        return creationTime;
    }
}


