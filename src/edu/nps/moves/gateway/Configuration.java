
package edu.nps.moves.gateway;

import java.util.*;

/**
 * Configuration settings for the gateway. The default settings here can be
 * overridden by settings in the GatewayConfiguration.properties file.
 * 
 * @author DMcG
 */
public class Configuration 
{
    // The ports the web server listen on
    /** The default unencrypted port the webserver listens on. */
    public static int HTTP_WEBSERVER_PORT = 8282;
    
    /** HTTPS port */
    public static int HTTPS_WEBSERVER_PORT = 443;
    
    // Use area of interest managment (an experimental feature)
    // or not. Unless you have an office in Watkins Hall, this 
    // is probably a bad feature to enable.
    
    /** Use area of interest managment or not */
    public static boolean  ENABLE_AOIM = false;
    
    // REDIS server settings. Redis is an in-memory message distribution
    // pub/sub server. A message may come in to one of the hosts in the
    // load balancing pool of websocket servers. That websocket server
    // forwards it to the redis server, which distributes it to the other
    // hosts in the pool.
    
    /** Whether to run the redis server at all. If we have a single host
     * there is no need to run a pub/sub server.
     */
    public static boolean ENABLE_REDIS = false;
    
    /** Host the redis server is running on */
    public static String REDIS_SERVER = "localhost";
    
    /** Port the redis server is listening on */
    public static int REDIS_PORT = 6379;
    
    /** Pub-sub channel to use. */
    public static String REDIS_PUB_SUB_CHANNEL = "DIS";
    
    // DIS native network settings
    
    
    /** Whether we should listen on the native UDP interface for DIS
     * at all. In some instances this may be a bad idea, for example
     * when there are no native DIS applications running on the server
     * side, and we only want to use the websocket server to communicate
     * between web applications.
     */
    public static boolean ENABLE_NATIVE_NETWORK_DIS = true;
    
    /** Whether to listen on the native network in multicast, or broadcast.
     *  Unicast is so rare as to not be worth bothering with.
     */
    public enum NativeNetworkMode  {BROADCAST, MULTICAST};
   
    /** bcast or mcast */
    public static NativeNetworkMode NETWORK_MODE = NativeNetworkMode.BROADCAST;
    
    /** DIS port. We also listen on the server's local network interface
     * for native DIS UDP packets on the network
     */
    public static int DIS_PORT = 3000;
    
    /** Multicast address to use with multicast */
    public static String MULTICAST_ADDRESS = "239.1.2.3";
    
    /**
     * The properties object passed in contains settings that override any
     * of those set above. The properties file is read at startup and passed
     * here. The name is GatewayConfiguration.properties.
     * 
     * @param settings 
     */
    public static void setConfigurationFromProperties(Properties settings)
    {

        try
        {
            HTTP_WEBSERVER_PORT = Integer.parseInt(settings.getProperty("webserverPort"));
            HTTPS_WEBSERVER_PORT = Integer.parseInt(settings.getProperty("httpsWebserverPort"));
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.out.println("webserver port not specified in GatewayConfiguration.properties, using default of " + HTTP_WEBSERVER_PORT + " and " + HTTPS_WEBSERVER_PORT);
        }
        
        String use_aoim = settings.getProperty("enableAOIM");
        if(use_aoim != null)
        {
            if(use_aoim.toUpperCase().equals("FALSE"))
                ENABLE_AOIM = false;
            
            if(use_aoim.toUpperCase().equals("TRUE"))
                ENABLE_AOIM = true;
        }
        
        // DIS settings for native network
        try
        {
            String useNativeDIS = settings.getProperty("enableNativeDIS");
            if(useNativeDIS != null)
            {
                if(useNativeDIS.equalsIgnoreCase("false"))
                    ENABLE_NATIVE_NETWORK_DIS = false;

                if(useNativeDIS.equalsIgnoreCase("true"))
                    ENABLE_NATIVE_NETWORK_DIS = true;
            }
            
            DIS_PORT = Integer.parseInt(settings.getProperty("disPort"));
            
            String mcastString = settings.getProperty("multicastAddress");
            if(mcastString != null)
            {
                MULTICAST_ADDRESS = mcastString;
            }
            
            String mode = settings.getProperty("nativeNetworkMode");
            if(mode != null)
            {
                mode = mode.toUpperCase();
                switch(mode)
                {
                    case "BROADCAST":
                        NETWORK_MODE = NativeNetworkMode.BROADCAST;
                        break;
                        
                    case "MULTICAST":
                        NETWORK_MODE = NativeNetworkMode.MULTICAST;
                        
                    default:
                        System.out.println("Unrecognized network mode in configuration file, should be either broadcast or multicast");
                }
            }
            
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.out.println("Problem in confgiuration file with DIS settings");
        }
        
        // Redis server settings
        
        try
        {
            String use_redis = settings.getProperty("enableRedis");
            if(use_redis != null)
            {
                if(use_redis.equalsIgnoreCase("false"))
                    ENABLE_REDIS = false;
                
                if(use_redis.equalsIgnoreCase("true"))
                    ENABLE_REDIS = true;
            }
            
            String redisHost = settings.getProperty("redisHost");
            if(redisHost != null)
                REDIS_SERVER = redisHost;
            
            String redisPort = settings.getProperty("redisPort");
            if(redisPort != null)
            {
                REDIS_PORT = Integer.parseInt(redisPort);
            }
            
            String pubSub = settings.getProperty("redisPubSubChannel");
            if(pubSub != null)
            {
                REDIS_PUB_SUB_CHANNEL = pubSub;
            }  
        }
        catch(Exception e)
        {
            e.printStackTrace();
            System.out.println("Problem in configuration file redis settings");
        }
        
        System.out.println(Configuration.class);
        
    }
  
}
