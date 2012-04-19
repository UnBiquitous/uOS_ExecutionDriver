package org.unbiqitous.driver.execution;

import static org.junit.Assert.*;

import java.io.DataInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.io.PipedInputStream;
import java.io.PipedOutputStream;
import java.io.Serializable;

import org.junit.Before;
import org.junit.Test;
import org.unbiquitous.driver.execution.ExecutionDriver;

import br.unb.unbiquitous.ubiquitos.uos.application.UOSMessageContext;
import br.unb.unbiquitous.ubiquitos.uos.messageEngine.messages.ServiceResponse;

public class ExecutionDriverTest_executeAgent {

	private ExecutionDriver driver;
	private ServiceResponse response;
	
	@Before public void setUp(){
		driver = new ExecutionDriver();
		response = new ServiceResponse();
		
	}
	
	@Test public void runTheCalledAgent() throws Exception{
		Agent a = new MyAgent();
		
		Integer before = AgentSpy.count;
		
		driver.executeAgent(null,response,createAgentMockContext(a));
		
		assertNull("No error should be found.",response.getError());
		assertEquals((Integer)(before+1),AgentSpy.count);
	}

	@Test public void dontAcceptANonAgentAgent() throws Exception{
		NonAgent a = new NonAgent();
		
		driver.executeAgent(null,response,createAgentMockContext(a));
		
		assertNotNull("An error is expected.",response.getError());
		assertEquals("The informed Agent is not a valid one.",response.getError());
	}
	
	@Test public void dontBreakWithoutAnAgent() throws Exception{
		driver.executeAgent(null,response,new UOSMessageContext(){
			public DataInputStream getDataInputStream() {
				return null;
			}
		});
		
		assertNotNull("An error is expected.",response.getError());
		assertEquals("No Agent was tranfered.",response.getError());
	}
	
	@Test public void dontBreakWhenSomethingBadHappens() throws Exception{
		driver.executeAgent(null,response,new UOSMessageContext(){
			public DataInputStream getDataInputStream() {
				throw new RuntimeException();
			}
		});
		
		assertNotNull("An error is expected.",response.getError());
		assertEquals("Something unexpected happened.",response.getError());
	}
	
	private UOSMessageContext createAgentMockContext(Serializable a)
			throws IOException {
		final PipedInputStream in = new PipedInputStream();
		new ObjectOutputStream(new PipedOutputStream(in)).writeObject(a);
		
		return new UOSMessageContext(){
			public DataInputStream getDataInputStream() {
				return new DataInputStream(in);
			}
		};
	}
}

class NonAgent implements Serializable{
	private static final long serialVersionUID = -6537712082673542107L;
}

class AgentSpy{
	static Integer count = 0;
}

class MyAgent implements Agent{
	private static final long serialVersionUID = -8267793981973238896L;
	
	public void run(){
		AgentSpy.count++;
	}
}