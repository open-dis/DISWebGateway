
package edu.nps.moves.gateway;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;
import javax.script.ScriptException;
import javax.script.*;

// Apache commons for codecs
import org.apache.commons.codec.binary.Base64;


/**
 * Solve area of interest management once and for all. Somebody (the client,
 * maybe the server) provides a chunk of javascript text that contains a
 * function called "aoim" and takes an argument of a PDU, and returns a
 * boolean. Java starts a Nashorn javascript interpreter and loads the 
 * javascript. Later the aoim function is invoked by Java to determine
 * whether the PDU should be sent to the client.<p>
 * 
 * Basically, stop dicking around trying to be efficient and instead be general.
 * The expectation is that this will run on a load-balanced cluster in
 * the cloud, so if it starts to consume a lot of CPU or memory, just
 * start another damn instance. The Javascript can be arbitrarily complex
 * or simple, and filter based on PDU type, entity type, position, or whatever
 * you like. And the performance isn't actually all that bad.
 * 
 * Still highly experimental and commented out for everyone but developers.
 * 
 * @author mcgredo
 */
public class AreaOfInterest 
{
  /** 
   * String that represents the javascript chunk we will execute to determine
   * if we should pass the PDU to this client
   */
  String javascriptFunctionText;
  
  /**
   * We evaluate the javascript in the engine, then get this invocable function
   * that can be called from Java to the "aoim" function in the javascript.
   * Note that this requires JDK 1.8u20-ish to get all the Nashorn features we want,
   * including javascript typed arrays. See
   * https://wiki.openjdk.java.net/display/Nashorn/Nashorn+extensions
   */
  Invocable invocableJavascriptFunction;
  
  public AreaOfInterest(String javascriptFunction)
  {
      if(javascriptFunction == null)
      {
          this.javascriptFunctionText = null;
          this.invocableJavascriptFunction = null;
          return;
      }
      
      try
      {
        ScriptEngineManager factory = new ScriptEngineManager();
        ScriptEngine engine = factory.getEngineByName("nashorn");
        this.invocableJavascriptFunction = (Invocable) engine;

        //javascriptFunction = "load ('scripts/dis7.js')\n" + javascriptFunction;
        this.javascriptFunctionText = javascriptFunction;

        engine.eval(this.javascriptFunctionText);
        
      }
      catch(Exception e)
      {
          System.out.println(e);
      }
  }
  
  /**
   * Send a raw PDU to be processed by the javascript code. Returns either
   * true or false, if true the PDU should be sent to the client. If there
   * is no javascript, a null AOIM, always pass all PDUs.
   * 
   * @param pduData PDU data, in binary form
   * @return true of the PDU should be sent to the client
   */
  public boolean pduPassesAOIM(byte[] pduData)
  {
      //System.out.println("entering pduPassesAoim");
      if(this.invocableJavascriptFunction == null)
          return true;
      
      try
      {
        Object result = invocableJavascriptFunction.invokeFunction("aoim", pduData);
        
        System.out.println("result of AOIM is " + result);
        
        if(result instanceof Boolean )
            return (Boolean)result;
      }
      catch(Exception e)
      {
          System.out.println(e);
      }
      
      return false;
  }
}
