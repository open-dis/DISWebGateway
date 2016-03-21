
package edu.nps.moves.gateway;

import java.net.InetAddress;
import javax.servlet.DispatcherType;
import org.eclipse.jetty.http.HttpVersion;
import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.ContextHandler;
import org.eclipse.jetty.server.handler.HandlerCollection;
import org.eclipse.jetty.util.resource.FileResource;
import org.eclipse.jetty.util.resource.Resource;
import org.eclipse.jetty.server.handler.RequestLogHandler;
import org.eclipse.jetty.server.handler.ResourceHandler;
import org.eclipse.jetty.server.handler.DefaultHandler;
import org.eclipse.jetty.websocket.server.WebSocketHandler;
import org.eclipse.jetty.websocket.servlet.WebSocketServletFactory;
import org.eclipse.jetty.webapp.WebAppContext;
import org.eclipse.jetty.util.ssl.SslContextFactory;
import org.eclipse.jetty.servlets.CrossOriginFilter;
import org.eclipse.jetty.servlet.ServletContextHandler;
import org.eclipse.jetty.servlet.FilterHolder;
import org.eclipse.jetty.servlet.ServletHolder;
import org.eclipse.jetty.servlet.DefaultServlet;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.EnumSet;
import java.net.*;
import java.io.*;

/**
 * This is a full-blown web server that happens to also handle websocket
 * connections on the server side. Websocket connections are handed off
 * to a subclass for handling.
 * 
 * Lifted from http://amilamanoj.blogspot.com/2013/06/secure-websockets-with-jetty.html
 * 
 * @author DMcG
 */
public class WebSocketServer 
{
    
    /** The web server we're creating */
    private Server server;
    
    /** Configuration properties, read from GatewayConfiguration.properties
     *  at startup.
     */
    public static Properties configurationProperties;
    
    /** The handlers--the type of content the web server can serve up */
    private static List<Handler> webSocketHandlerList = new ArrayList<>();

    /** 
     * Entry point. Create a new Server class, initialize it, and start it.
     * @param args None, just a sig
     * @throws Exception catch-all
     */
    public static void main(String[] args) throws Exception 
    {
        // Get configurationProperties properties
        configurationProperties = new Properties();
        InputStream in = new FileInputStream("GatewayConfiguration.properties");
        configurationProperties.load(in);
        in.close();
        
        System.out.println("Loaded configuration properties");
        Configuration.setConfigurationFromProperties(configurationProperties);
        

        
        // Basic Jetty server
        org.eclipse.jetty.server.Server server = new org.eclipse.jetty.server.Server();
        
        // Loading the various features needs to be modularized and integrated
        // with the configuration properties file
        
        // Add handlers for the various things a web server can do: basic
        // http, websockets, etc.
        
        // This configuration problem has grown out of control and needs to be 
        // modularized and better configured. The handlers need to be added
        // in a systematic way and the list of recipients of network messages--
        // the various types of DISEndpoints such as web pages, native network,
        // and JEDIS--needs to be handled better.
        
        // Http server (plaintext, non-encrypted)
        HttpConfiguration httpConfiguration = new HttpConfiguration();
        ServerConnector httpConnector = new ServerConnector(server, new HttpConnectionFactory(httpConfiguration));
        
        httpConnector.setPort(Configuration.HTTP_WEBSERVER_PORT);
        
        
        
        // Configure https connection. Create a self signed certificate with
        // keytool -genkey -alias sitename -keyalg RSA -keystore keystore.jks -keysize 2048
        HttpConfiguration https = new HttpConfiguration();
        https.addCustomizer(new SecureRequestCustomizer());
        
        // Set up crypto configurationProperties
        SslContextFactory sslContextFactory = new SslContextFactory();
        sslContextFactory.setKeyStorePath("keystore.jks");

        sslContextFactory.setKeyStorePassword("123456");
        sslContextFactory.setKeyManagerPassword("123456");
        
        ServerConnector httpsConnector = new ServerConnector(server,
                new SslConnectionFactory(sslContextFactory, "http/1.1"),
                new HttpConnectionFactory(https));
        httpsConnector.setPort(Configuration.HTTPS_WEBSERVER_PORT);
        
        
        // Tell server about connections
        Connector[] connectors = {httpConnector, httpsConnector};
        server.setConnectors(connectors);
        
        // Set up a websocket handler. Incoming requests to ws://
        // will be handed off to this class.
        WebSocketHandler wsHandler = new WebSocketHandler() 
        {
            @Override
            public void configure(WebSocketServletFactory webSocketServletFactory) 
            {
                webSocketServletFactory.register(WebPageConnection.class);
            }
        };
        
        // Add it to the handler list
        ContextHandler wsContextHandler = new ContextHandler();
        wsContextHandler.setHandler(wsHandler);
        //wsContextHandler.setContextPath("/nve");  // this context path doesn't work ftm
        webSocketHandlerList.add(wsHandler);
        
        // Add a static content (html) handler. Html and other files
        // go in the content directory.
        ResourceHandler resourceHandler = new ResourceHandler();
        resourceHandler.setDirectoriesListed(true);
        
        // We can do caching. Turn that off for now, because running experimental
        // software with caching is error-prone; it's too easy to get old
        // software loaded.
        //resourceHandler.setCacheControl("max-age=604800");
        //resourceHandler.setEtags(true);
       
        
        resourceHandler.setWelcomeFiles(new String[] {"index.html", "index.htm"} );
        resourceHandler.setResourceBase("./content");
        webSocketHandlerList.add(resourceHandler);
        
        // Add a JSP handler. JSP can be handy for determining the internal
        // state of this server from a web page and displaying it to the user
        WebAppContext jspContext = new WebAppContext();
        jspContext.setWelcomeFiles(new String[]  {"index.jsp"});
        jspContext.setResourceBase("./content");
        jspContext.setContextPath("/");
        webSocketHandlerList.add(jspContext);
        
       
        // This is some magic to get CORS working. CORS inserts a header on the
        // server side which instructs the client that it's OK to load resources
        // on the supplied page from another source. For example, a page loaded
        // from localhost may contain a reference to http://savage.nps.edu/savage/model.x3d.
        // This is normally disallowed (since it doesn't come from the same source)
        // but the below puts in a header, but default, that allows this. It can
        // be restricted more; see http://stackoverflow.com/questions/28190198/cross-origin-filter-with-embedded-jetty
        // and http://archive.eclipse.org/jetty/9.0.0.M0/apidocs/org/eclipse/jetty/servlets/CrossOriginFilter.html
        
        ServletContextHandler defaultContext = new ServletContextHandler();
        FilterHolder cors = defaultContext.addFilter(CrossOriginFilter.class,"/*",EnumSet.of(DispatcherType.REQUEST));
        cors.setInitParameter(CrossOriginFilter.ALLOWED_ORIGINS_PARAM, "*");
        cors.setInitParameter(CrossOriginFilter.ACCESS_CONTROL_ALLOW_ORIGIN_HEADER, "*");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_METHODS_PARAM, "GET,POST,HEAD");
        cors.setInitParameter(CrossOriginFilter.ALLOWED_HEADERS_PARAM, "X-Requested-With,Content-Type,Accept,Origin");

        // Use a DefaultServlet to serve static files.
        // Alternate Holder technique, prepare then add.
        // DefaultServlet should be named 'default'
        ServletHolder defaultServletHolder = new ServletHolder("default", DefaultServlet.class);
        defaultServletHolder.setInitParameter("resourceBase","./http/");
        defaultServletHolder.setInitParameter("dirAllowed","false");
        defaultContext.addServlet(defaultServletHolder,"/");
        
        webSocketHandlerList.add(defaultContext);

        // Logging
        RequestLogHandler requestLogHandler = new RequestLogHandler();
        NCSARequestLog requestLog = new NCSARequestLog("./logs/jetty-yyyy_mm_dd.request.log");
        requestLog.setRetainDays(90);
        requestLog.setAppend(true);
        requestLog.setExtended(false);
        requestLog.setLogTimeZone("GMT");
        requestLogHandler.setRequestLog(requestLog);
       
        webSocketHandlerList.add(requestLogHandler);

         // Create a default handler for everything else
        //DefaultHandler defaultHandler = new DefaultHandler();
        //webSocketHandlerList.add(defaultHandler);
        
        
       // Add the handlers we created above to the server. The order in which they're
       // added is significant; the web server travels down the handler list until
       // it finds a match.
        HandlerCollection handlerCollection = new HandlerCollection();
        handlerCollection.setHandlers(webSocketHandlerList.toArray(new Handler[0]));
        server.setHandler(handlerCollection);

        // We've configured the web server to manage several types of content: html,
        // jsp pages, and web sockets.
        
        // Start listening for native DIS on the local TCP/IP network.
        if(Configuration.ENABLE_NATIVE_NETWORK_DIS)
        {
            DisNative listener = null;
        
            
            if(Configuration.NETWORK_MODE == Configuration.NativeNetworkMode.BROADCAST)
            {
                MulticastSocket s = DisSocketFactory.getDisSocket(Configuration.DIS_PORT, null);
                listener = new DisNative(s, null, Configuration.DIS_PORT);
            }
            else if(Configuration.NETWORK_MODE == Configuration.NativeNetworkMode.MULTICAST)
            {
                MulticastSocket s = DisSocketFactory.getDisSocket(Configuration.DIS_PORT, Configuration.MULTICAST_ADDRESS);
                InetAddress addr = InetAddress.getByName(Configuration.MULTICAST_ADDRESS);
                listener = new DisNative(s, addr, Configuration.DIS_PORT);
            }
            else
            {
                System.out.println("Unrecognized network mode");
            }
                   
        
        
            // Run the native listening thread 
            Thread aThread = new Thread(listener);
            aThread.start();
            System.out.println("Started DIS listening on UDP port " + Configuration.DIS_PORT);
       
        
            // Add the native DIS network to the list of things that will be notified
            // if a packet arrives from a web client
            ConnectionManager.getConnectionManager().addConnection(listener);
        
        }
        
          // Experimental work with a redis server for scalable back-end
          // websocket cloud servers
        try
        {
            if(Configuration.ENABLE_REDIS)
            {
                DisRedis instance = DisRedis.getInstance();
                ConnectionManager.getConnectionManager().addConnection(instance);
                System.out.println("started redis instance");
            }
            else
            {
                System.out.println("Configured to not use redis server");
            }
        }
        catch(Exception e)
        {
            System.out.println("Unable to contact a redis server, continuing");
        }
       
 
        // Start the http server
        System.out.println("Starting websocket server on TCP port " + Configuration.HTTP_WEBSERVER_PORT);
        System.out.println("Starting https server on TCP port " + Configuration.HTTPS_WEBSERVER_PORT);
        server.start();
        server.join();
        
        
    }

   


}
