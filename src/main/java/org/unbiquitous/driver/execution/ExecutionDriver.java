package org.unbiquitous.driver.execution;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.util.List;

import org.apache.log4j.Logger;
import org.luaj.vm2.LoadState;
import org.luaj.vm2.lib.jse.JsePlatform;

import br.unb.unbiquitous.ubiquitos.uos.adaptabitilyEngine.Gateway;
import br.unb.unbiquitous.ubiquitos.uos.application.UOSMessageContext;
import br.unb.unbiquitous.ubiquitos.uos.driverManager.UosDriver;
import br.unb.unbiquitous.ubiquitos.uos.messageEngine.dataType.UpDriver;
import br.unb.unbiquitous.ubiquitos.uos.messageEngine.dataType.UpService.ParameterType;
import br.unb.unbiquitous.ubiquitos.uos.messageEngine.messages.ServiceCall;
import br.unb.unbiquitous.ubiquitos.uos.messageEngine.messages.ServiceResponse;

public class ExecutionDriver implements UosDriver {

	private static final Logger logger = Logger.getLogger(ExecutionDriver.class);
	
	private long script_id = 0;

	private UpDriver driver;
	private Gateway gateway;
	
	public ExecutionDriver(){
		driver = new UpDriver("uos.ExecutionDriver");
		driver.addService("remoteExecution").addParameter("code", ParameterType.MANDATORY);
		driver.addService("executeAgent");
	}
	
	public UpDriver getDriver() {	return driver;	}

	public void init(Gateway gateway, String instanceId) {this.gateway = gateway;}

	public void destroy() {}

	public List<UpDriver> getParent() {	return null;	}
	
	public void remoteExecution(ServiceCall call, ServiceResponse response,
			UOSMessageContext object) {
		try {
			script_id++;
			
			for(String key: call.getParameters().keySet()){
				UosLuaCall.values().setValue(script_id, key,call.getParameter(key));
			}
			
			StringBuffer script = new StringBuffer();
			script.append("UOS_ID="+(script_id)+"\n");
			script.append("require( '"+UosLuaCall.class.getName()+"' )\n");
			script.append("function set( key, value) \n");
			script.append("	Uos.set(UOS_ID,key,value)\n");
			script.append("end\n");
			script.append("function get( key) \n");
			script.append("	return Uos.get(UOS_ID,key)\n");
			script.append("end\n");
			script.append(call.getParameter("code"));
			InputStream file = new StringInputStream(script.toString());
			LoadState.load( file, "script_"+script_id, JsePlatform.standardGlobals() ).call();
			
			response.addParameter("value", UosLuaCall.values().getValue(script_id, "value"));
		} catch (IOException e) {
			logger.error("Error handling Execution call. Cause:",e);
			response.setError("Error handling Execution call. Cause:"+e.getMessage());
		}
	}

	public void executeAgent(ServiceCall call, ServiceResponse response,
			UOSMessageContext ctx) {
		try {
			if(ctx.getDataInputStream() == null){
				response.setError("No Data Stream, containing agent, was found.");
				return;
			}
			final DataInputStream stream = ctx.getDataInputStream();
			new Thread(){
				public void run() {
					Object o = null;
					try {
						while (stream.available() == 0){
							System.out.print('n');
						}
						ObjectInputStream reader = new ObjectInputStream(stream);
							o = reader.readObject();
					} catch (Exception e) {
						// TODO Auto-generated catch block
						e.printStackTrace();
					}
					if (o instanceof Agent){
						final Agent a = ((Agent)o);
						a.run(gateway);
					}else{
						//response.setError("The informed Agent is not a valid one.");
					}
					};
			}.start();
		} catch (Throwable e) {
			response.setError("Something unexpected happened.");
			e.printStackTrace();
		}
	}

}
