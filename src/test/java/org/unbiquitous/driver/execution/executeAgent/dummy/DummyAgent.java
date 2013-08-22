package org.unbiquitous.driver.execution.executeAgent.dummy;

import org.unbiquitous.driver.execution.executeAgent.Agent;
import org.unbiquitous.driver.execution.executeAgent.ExecuteAgentServiceTest;
import org.unbiquitous.uos.core.adaptabitilyEngine.Gateway;


/**
 *	This class is just a Dummy used at the {@link ExecuteAgentServiceTest}
 *  
 * @author Fabricio Nogueira Buzeto
 */
public class DummyAgent extends Agent {
	private static final long serialVersionUID = -6366707922217209685L;

	public void run(Gateway gateway) {}

}
