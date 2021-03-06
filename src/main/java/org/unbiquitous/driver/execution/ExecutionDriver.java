package org.unbiquitous.driver.execution;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectStreamClass;
import java.io.StringReader;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.luaj.vm2.Globals;
import org.luaj.vm2.lib.jse.JsePlatform;
import org.unbiquitous.driver.execution.executeAgent.Agent;
import org.unbiquitous.driver.execution.executeAgent.ClassToolbox;
import org.unbiquitous.driver.execution.executeAgent.GatewayMap;
import org.unbiquitous.driver.execution.remoteExecution.UosLuaCall;
import org.unbiquitous.uos.core.InitialProperties;
import org.unbiquitous.uos.core.adaptabitilyEngine.Gateway;
import org.unbiquitous.uos.core.applicationManager.CallContext;
import org.unbiquitous.uos.core.driverManager.UosDriver;
import org.unbiquitous.uos.core.messageEngine.dataType.UpDriver;
import org.unbiquitous.uos.core.messageEngine.dataType.UpService.ParameterType;
import org.unbiquitous.uos.core.messageEngine.messages.Call;
import org.unbiquitous.uos.core.messageEngine.messages.Response;


/**
 * This driver enables Code Mobility to the middleware.
 * Using it the Execution Capability of a Device is shared as a resource
 * for others to take advantge.
 * 
 * Its services are:
 * 
 * {@link #remoteExecution(Call, Response, UOSMessageContext)}
 * 
 * This service expects a Lua script through the "code" parameter. Any 
 * other parameter is available for the execution of the scripted code.
 * Access to the parameters is done through the <code>get(<key>)</code> function.
 * The response parameters are set using the <code>set(<key>)</code> function.
 * 
 * {@link #executeAgent(Call, Response, UOSMessageContext)}
 * 
 * This service receives a Serialized {@link Agent} on channel 0 and resumes its 
 * execution. No other class must be transfered, aside from the Agent itself, 
 * uOS classes and JDK classes. Any other class should be transient to the object.
 * 
 * In case of the Agent to be of a class not present on the targeted device.
 * The class can be transfered using the channel 1 and informing the class name
 * through the "class" parameter
 * 
 * OBS: For using ExecutionDriver on Android please see {@link ClassToolbox#platform}
 * 
 * @author Fabricio Nogueira Buzeto
 *
 */
public class ExecutionDriver implements UosDriver {

	private static final Logger logger = Logger.getLogger(ExecutionDriver.class.getName());
	
	private long script_id = 0;

	private UpDriver driver;
	private Gateway gateway;
	private ClassToolbox toolbox;
	
	public ExecutionDriver(){
		this(new ClassToolbox());
		toolbox.add2BlackList("junit");
		toolbox.add2BlackList("log4j");
		toolbox.add2BlackList("hsqldb");
		toolbox.add2BlackList("owlapi");
		toolbox.add2BlackList("jcl-core");
		toolbox.add2BlackList("objenesis");
		toolbox.add2BlackList("cglib-nodep");
		toolbox.add2BlackList("HermiT");
		toolbox.add2BlackList("hamcrest-core");
		toolbox.add2BlackList("mockito-all");
		toolbox.add2BlackList("uos-core");
		toolbox.add2BlackList("uos.tcp_udp.plugin");
		toolbox.add2BlackList("/uos_core/target/classes");
		toolbox.add2BlackList("uos-execution");
	}
	
	public ExecutionDriver(ClassToolbox myBox) {
		this.toolbox = myBox;
		driver = new UpDriver("uos.ExecutionDriver");
		driver.addService("remoteExecution").addParameter("code", ParameterType.MANDATORY);
		driver.addService("executeAgent");
		driver.addService("listKnownClasses");
	}

	public ClassToolbox toolbox() {return toolbox;}
	
	public UpDriver getDriver() {	return driver;	}

	public void init(Gateway gateway, InitialProperties properties, String instanceId) {this.gateway = gateway;}

	public void destroy() {}

	public List<UpDriver> getParent() {	return null;	}
	
	public void remoteExecution(Call call, Response response,
			CallContext object) {
		try {
			script_id++;
			
			for(String key: call.getParameters().keySet()){
				UosLuaCall.values().setValue(script_id, key,call.getParameterString(key));
			}
			
			StringBuffer script = new StringBuffer();
			script.append("UOS_ID="+(script_id)+"\n");
			script.append("require '"+UosLuaCall.class.getName()+"' \n");
			script.append("function set( key, value) \n");
			script.append("	Uos.set(UOS_ID,key,value)\n");
			script.append("end\n");
			script.append("function get( key) \n");
			script.append("	return Uos.get(UOS_ID,key)\n");
			script.append("end\n");
			script.append(call.getParameter("code"));
			Globals _G = JsePlatform.standardGlobals();
			_G.load(new StringReader(script.toString()), "script_"+script_id).call();
			
			response.addParameter("value", UosLuaCall.values().getValue(script_id, "value"));
		} catch (Exception e) {
			logger.log(Level.SEVERE,"Error handling Execution call. Cause:",e);
			response.setError("Error handling Execution call. Cause:"+e.getMessage());
		}
	}

	public void listKnownClasses(Call call, Response response,
			CallContext ctx) {
		response.addParameter("classes", toolbox.listKnownClasses());
	}
	
	public void executeAgent(Call call, Response response,
			CallContext ctx) {
		try {
			if(ctx.getDataInputStream() == null){
				response.setError("No Data Stream, containing agent, was found.");
				return;
			}
			final DataInputStream agent = ctx.getDataInputStream();
			final DataInputStream clazz;
			
			boolean hasJar = false;
			String className = null;
			if (call.getParameter("jar") != null) hasJar = true;
			else	className = call.getParameterString("class");
			
			/*Question: 
					Should it receive the size of class/jar ? 
					This could aid avoiding caching problems and even could 
					help checking if the received data is OK.
			 */
			if (hasJar || className != null){
				clazz = ctx.getDataInputStream(1);
			}else{
				clazz = null;
			}
			new Thread(new AgentHandler(className, clazz, agent)).start();
		} catch (Throwable e) {
			response.setError("Something unexpected happened.");
			logger.log(Level.SEVERE,"Problems executing agent.",e);
		}
	}

	class AgentHandler implements Runnable{
		private String className;
		private DataInputStream clazz;
		private DataInputStream agent;
		
		public AgentHandler(String className, DataInputStream clazz,DataInputStream agent) {
			this.className = className;
			this.clazz = clazz;
			this.agent = agent;
		}

		public void run() {
			try {
				final ClassLoader loader;
				if (className != null){
					while (clazz.available() == 0){}
					loader = toolbox.load(className, clazz);
				}else if (clazz != null){
					loader = toolbox.load(clazz);
				}else{
					loader = null;
				}
				while (agent.available() == 0){}
				ObjectInputStream reader = new ObjectInputStream(agent){
					@Override
					protected Class<?> resolveClass(ObjectStreamClass desc)
							throws IOException, ClassNotFoundException {
						try{
							return loader.loadClass(desc.getName());
						}catch(Exception e){
							return super.resolveClass(desc);
						}
					}
				};
				Object o = reader.readObject();
				if (o instanceof Agent){
					((Agent)o).run(gateway);
				}else{
					Method run = o.getClass().getMethod("run", Map.class);
					run.invoke(o, new GatewayMap(gateway));
				}
			} catch (Exception e) {
				logger.log(Level.SEVERE,"Problems on running agent",e);
			}
		};
	}

}
