package edu.nps.moves.gateway;

import javax.script.Invocable;
import javax.script.ScriptEngine;
import javax.script.ScriptEngineManager;

/**
 * This is a general way to encapsulate a javascript script, and invoke it
 * from Java. It can be used for area of interest management/distributed
 * data management, for example deciding whether to forward a PDU to a 
 * client. It can also be used as a gateway, for example by changing the
 * entity type contained in a PDU to something understood by the client.
 * 
 * Uses the Nashorn javascript engine included in Java 8. Supply it with
 * the text of a javascript function, then invoke a Java method.
 * 
 * @author DMcG
 */
public class JavascriptFilter 
{
  /** 
   * String that represents the javascript chunk we will execute to determine
   * if we should pass the PDU to this client
   */
  String javascriptFunctionText;
  
  /** The name of the javascript function in the above script to invoke */
  String functionToInvoke;
  
  /**
   * We evaluate the javascript in the engine, then get this invocable function
   * that can be called from Java to the "aoim" function in the javascript.
   * Note that this requires JDK 1.8u20-ish to get all the Nashorn features we want,
   * including javascript typed arrays. See
   * https://wiki.openjdk.java.net/display/Nashorn/Nashorn+extensions
   */
  Invocable invocableJavascriptFunction;
  
  /**
   * Constructor. Pass in the text of the javascript function and the 
   * name of the function in the javascript to invoke.
   * @param javascriptFunction
   * @param functionToInvoke 
   */
  public JavascriptFilter(String javascriptFunction, String functionToInvoke)
  {
      this.javascriptFunctionText = null;
      this.invocableJavascriptFunction = null;
      this.functionToInvoke = functionToInvoke;
    
      try
      {
        ScriptEngineManager factory = new ScriptEngineManager();
        ScriptEngine engine = factory.getEngineByName("nashorn");
        this.invocableJavascriptFunction = (Invocable) engine;

        this.javascriptFunctionText = javascriptFunction;

        engine.eval(this.javascriptFunctionText);
        
      }
      catch(Exception e)
      {
          System.out.println(e);
      }
  }
  
  /**
   * Send a raw PDU to be processed by the javascript code. 
   * The javascript code returns an object, and the caller
   * should know what that object is by context, and cast it
   * to what it expects. What could go wrong?
   * 
   * @param pduData PDU data, in binary form
   * @return true of the PDU should be sent to the client
   */
  public Object filterPdu(byte[] pduData) 
  {
      if( (this.invocableJavascriptFunction == null) || (this.functionToInvoke == null) )
          throw new RuntimeException("No javascript text or no function name");
      
      try
      {
        Object result = invocableJavascriptFunction.invokeFunction("aoim", pduData);        
        return result;
      }
      catch(Exception e)
      {
          System.out.println(e);
      }
      
      return null;
  }
  
  /**
   * A cover method for filterPdu. Pass in the binary representation
   * of a PDU, get back either true or false. This is typically used
   * for thing like DDM/area of interest management, where you want
   * either a thumbs-up or thumbs-down on whether to send a PDU to
   * a client.
   * 
   * @param buffer
   * @return true if the PDU should be passed, false if not. IN the event of a configuration error, it's passed.
   */
  public boolean passPdu(byte[] buffer)
  {
      Object result = this.filterPdu(buffer);
      if(result != null && result instanceof Boolean)
      {
          Boolean booleanResult = (Boolean)result;
          return booleanResult;
      }
      
      // Some sort of configuration error? return true.
      return true;
  }
  
  /**
   * Passes the PDU to a javascript function that can modify the
   * contents of the PDU. This lets you implement a gateway in 
   * javascript, for example by rewriting the EntityType or 
   * any other type of arbitrary rewrite of a PDU
   * @param buffer
   * @return The modified PDU. If there's a configruation error, the original PDU is returned
   */
  public byte[] modifyPdu(byte[] buffer)
  {
      Object result = this.filterPdu(buffer);
      if(result != null && result instanceof byte[])
      {
          byte[] modifiedPdu = (byte[])result;
          return modifiedPdu;
      }
      
      // Some sort of configuration error? return the original PDU.
      return buffer;
  }
  
  public void printFilter()
  {
      System.out.println("Function to invoke: " + this.functionToInvoke);
      System.out.println(this.javascriptFunctionText);
  }

}
