package org.unbiquitous.driver.execution.executeAgent;

import static junit.framework.Assert.assertEquals;
import static org.fest.assertions.api.Assertions.assertThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.junit.Before;
import org.junit.Test;
import org.unbiquitous.uos.core.adaptabitilyEngine.Gateway;
import org.unbiquitous.uos.core.adaptabitilyEngine.UosEventListener;
import org.unbiquitous.uos.core.driverManager.DriverData;
import org.unbiquitous.uos.core.messageEngine.dataType.UpDevice;
import org.unbiquitous.uos.core.messageEngine.dataType.UpDriver;
import org.unbiquitous.uos.core.messageEngine.messages.Call;
import org.unbiquitous.uos.core.messageEngine.messages.Response;

import com.fasterxml.jackson.databind.ObjectMapper;

public class GatewayMapTest {

	private static final ObjectMapper mapper = new ObjectMapper();

	private Gateway delegate;
	private GatewayMap map;
	private UpDevice device;
	private Call call;
	private Response response;

	@SuppressWarnings("rawtypes")
	@Before
	public void setUp() {
		delegate = mock(Gateway.class);
		map = new GatewayMap(delegate);
		GatewayMap.globals = new HashMap();
		device = new UpDevice("d1").addNetworkInterface("addr", "t");
		call = new Call("dr1", "s1", "i1").addParameter("p", "v");
		response = new Response().addParameter("teste", "t");
	}

	@Test
	public void convertMapPutToAServiceCall() throws Exception {
		Map<String, Object> callParams = new HashMap<String, Object>();
		callParams.put("device", mapper.valueToTree(device));
		callParams.put("serviceCall", mapper.valueToTree(call));

		when(delegate.callService(eq(device), eq(call))).thenReturn(response);

		assertThat(map.put("callService", callParams)).isEqualTo(mapper.readValue(response.toString(), Map.class));

		verify(delegate).callService(device, call);
	}

	@Test
	public void convertMapPutToAServiceCallWithFullParameters() throws Exception {
		String serviceName = "s1";
		String driverName = "d1";
		String instanceId = "i1";
		String securityType = "k1";
		Map<String, Object> parameters = new HashMap<String, Object>();

		Map<String, Object> callParams = new HashMap<String, Object>();
		callParams.put("device", mapper.valueToTree(device));
		callParams.put("serviceName", serviceName);
		callParams.put("driverName", driverName);
		callParams.put("instanceId", instanceId);
		callParams.put("securityType", securityType);
		callParams.put("parameters", parameters);

		when(delegate.callService(device, serviceName, driverName, instanceId, securityType, parameters))
				.thenReturn(response);

		assertThat(map.put("callService", callParams)).isEqualTo(mapper.readValue(response.toString(), Map.class));

		verify(delegate).callService(device, serviceName, driverName, instanceId, securityType, parameters);
	}

	@Test
	public void convertMapPutToARegisterForEvent() throws Exception {
		UosEventListener listener = mock(UosEventListener.class);
		String driver = "d1";
		String eventKey = "e1";

		Map<String, Object> callParams = new HashMap<String, Object>();
		callParams.put("listener", listener); //TODO: Maybe uOS Won't like this 
		callParams.put("device", mapper.valueToTree(device));
		callParams.put("driver", driver);
		callParams.put("eventKey", eventKey);

		map.put("registerForEvent", callParams);

		verify(delegate).register(listener, device, driver, eventKey);
	}

	@Test
	public void convertMapPutToARegisterForEventWithFullParameters() throws Exception {
		UosEventListener listener = mock(UosEventListener.class);
		String driver = "d1";
		String instanceId = "i1";
		String eventKey = "e1";

		Map<String, Object> callParams = new HashMap<String, Object>();
		callParams.put("listener", listener); //TODO: Maybe uOS Won't like this 
		callParams.put("device", mapper.valueToTree(device));
		callParams.put("driver", driver);
		callParams.put("instanceId", instanceId);
		callParams.put("eventKey", eventKey);

		map.put("registerForEvent", callParams);

		verify(delegate).register(listener, device, driver, instanceId, eventKey);
	}

	//TODO: public void unregisterForEvent(UosEventListener listener) 
	//TODO: public void unregisterForEvent(UosEventListener listener, UpDevice device, String driver, String instanceId, String eventKey)
	//TODO: public void sendEventNotify(Notify notify, UpDevice device)

	@Test
	public void convertMapGetToAgetCurrentDevice() throws Exception {
		when(delegate.getCurrentDevice()).thenReturn(device);

		assertEquals(mapper.readValue(device.toString(), Map.class), (Map<?, ?>) map.get("getCurrentDevice"));

		verify(delegate).getCurrentDevice();
	}

	@Test
	public void convertMapPutToAgetCurrentDevice() throws Exception {
		when(delegate.getCurrentDevice()).thenReturn(device);

		assertEquals(mapper.readValue(device.toString(), Map.class), (Map<?, ?>) map.put("getCurrentDevice", null));

		verify(delegate).getCurrentDevice();
	}

	@Test
	public void convertMapPutToAlistDrivers() throws Exception {
		String driverName = "d1";
		String instanceID = "i1";
		List<DriverData> response = new ArrayList<DriverData>();
		UpDriver driver = new UpDriver("drv1");
		DriverData driverData = new DriverData(driver, device, instanceID);
		response.add(driverData);

		Map<String, Object> expected_data = new HashMap<String, Object>();
		expected_data.put("driver", mapper.readValue(driver.toString(), Map.class));
		expected_data.put("device", mapper.readValue(device.toString(), Map.class));
		expected_data.put("instanceID", instanceID);
		List<Map<String, Object>> expected = new ArrayList<Map<String, Object>>();
		expected.add(expected_data);

		when(delegate.listDrivers(driverName)).thenReturn(response);

		Map<String, Object> callParams = new HashMap<String, Object>();
		callParams.put("driverName", driverName);

		assertEquals(expected, map.put("listDrivers", callParams));

		verify(delegate).listDrivers(driverName);
	}

	@Test
	public void unkownPropertiesActAsGlobals() throws Exception {
		map.put("myNumber", 1);
		assertThat(map.get("myNumber")).isEqualTo(1);
		assertThat(new GatewayMap(null).get("myNumber")).isEqualTo(1);
	}
}
