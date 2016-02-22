# DIS_Map

This is a native Distributed Interactive Simulation (DIS) websockets 
implementation.

Websockets are Javascript implementations of TCP sockets
(plus some extra special sauce.) This enables Javascript running 
inside an HTML page can send and receive binary DIS messages over
TCP. The implications for M&S can be significant; with WebGL
this enables 3D graphics inside the web page. Even without 
3D graphics many useful applications can be created via web
mashups with, for example, Google Maps, Open Street Map, and
D3.js graphics.

This distribution uses Jetty (a Java-based web application server
similar to Apache Tomcat in functionality) to implement the server
side of websockets. The application is configured via the
GatewayConfiguration.properties file in the root directory.

Included are web pages that implement a simple Google Maps web
page that displays the location of DIS entities. These pages
are in the content directory. 

This distribution includes a Javascript implementation of DIS
that encodes and decodes the standard IEEE binary format. Thus
DIS messages from legacy applications can be  forwarded 
to the web page from the server side and decoded there.

There are a number of experimental features you probably shouldn't
mess with, including a Redis server for cloud-based distributions
that can scale, and an area of interest (AOIM)/distributed data management
(DDM) implementation that uses Javascript to filter packets on the
server side on a per-connection basis.

License is BSD. Copyright 2008-2016 MOVES Institute, Naval Postgraduate
School.


