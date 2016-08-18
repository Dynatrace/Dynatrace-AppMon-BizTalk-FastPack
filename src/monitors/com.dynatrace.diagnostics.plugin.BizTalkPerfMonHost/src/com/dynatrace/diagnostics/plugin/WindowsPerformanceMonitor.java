package com.dynatrace.diagnostics.plugin;

import java.security.InvalidParameterException;
import java.util.Collection;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.dynatrace.diagnostics.pdk.Monitor;
import com.dynatrace.diagnostics.pdk.MonitorEnvironment;
import com.dynatrace.diagnostics.pdk.MonitorMeasure;
import com.dynatrace.diagnostics.pdk.Status;
import com.dynatrace.diagnostics.plugin.perflib.PerformanceFactory;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.InvalidOperationException;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceCounter;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceCounterException;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceMeasureKey;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceMonitor;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceObject;

/**
 * Class for the Windows Performance Monitor Plugin which queries performance
 * counters from a windows machines. Querying counters on remote machines is
 * possible with the help of NET USE.
 */
public class WindowsPerformanceMonitor implements Monitor {

	private static final String AUTH = "auth";
	private static final String DOMAIN = "domain";
	private static final String PASSWORD = "password";
	private static final String USERNAME = "username";
	private static final String BIZTALK_INSTANCE = "biztalkinstance";

	private static final String MEASURE_CONFIG_STRING_OBJECT_NAME = "objectName";
	private static final String MEASURE_CONFIG_STRING_COUNTER_NAME = "counterName";
	private static final String MEASURE_CONFIG_STRING_SCALE = "scale";

	private static final Logger log = Logger.getLogger(WindowsPerformanceMonitor.class.getName());
	
	private PerformanceMonitor perfmon;
	private String instance = "";
	
	private PerformanceObject addMetric(MonitorMeasure measure) throws InvalidOperationException,
			PerformanceCounterException {
		
		String objectName = measure.getParameter(MEASURE_CONFIG_STRING_OBJECT_NAME);
		String counterName = measure.getParameter(MEASURE_CONFIG_STRING_COUNTER_NAME);
		String instanceName = instance;
		String scale = measure.getParameter(MEASURE_CONFIG_STRING_SCALE);
		PerformanceObject object = new PerformanceObject(objectName, counterName,
				scale != null && scale.equals("1000")
						? PerformanceCounter.SCALE_1000
						: PerformanceCounter.NO_SCALE,
				instanceName != null && !instanceName.isEmpty()
						? instanceName
						: null);
		
		perfmon.addQuery(object);				
		return object;
	}
	

	/**
	 * The setup method reads the configuration from the MonitorEnvironment and
	 * connects to the configured host. If authentication is enabled, the
	 * connection will be established using NET USE. If the username is empty,
	 * the setup will end with an error status. Then a number of performance
	 * metrics will be added to the query, which will be executed in the
	 * {@link #execute(MonitorEnvironment)} method.
	 *
	 * @param MonitorEnvironment
	 * @throws Exception
	 */
	@Override
    public Status setup(MonitorEnvironment env) throws Exception {
		try {
			this.perfmon = PerformanceFactory.createPerformanceMonitor();
		} catch (UnsupportedOperationException ex) {
			return new Status(Status.StatusCode.ErrorInfrastructure, "This collector does not support windows monitors", "This collector does not support windows monitors", ex);
		}

		boolean auth;
		String username;
		String password;
		String domain;
		String hostname;

		try {
			auth = env.getConfigBoolean(AUTH);
			username = env.getConfigString(USERNAME);
			if (auth && username.length() == 0)
				throw new InvalidParameterException("empty username");

			password = env.getConfigPassword(PASSWORD);
			domain = env.getConfigString(DOMAIN);
			hostname = env.getHost().getAddress();

			instance = env.getConfigString(BIZTALK_INSTANCE);
			if (instance.length() == 0)
				throw new InvalidParameterException("empty instance, please enter the BizTalk instance to monitor.");

		} catch (NullPointerException ex) {
			return new Status(Status.StatusCode.ErrorInternal, "Missing configuration property", "Missing configuration property", ex);
		} catch (InvalidParameterException ipe) {
			return new Status(Status.StatusCode.ErrorInternal, "Invalid configuration property", "Invalid configuration property", ipe);
		}

		try {
			if (auth) {
				perfmon.connect(hostname, domain, username, password);
			} else {
				perfmon.connect(hostname);
			}
		} catch (Exception ex) {
			return new Status(Status.StatusCode.ErrorInfrastructure, "Connection problem", "Connecting to the host '" + hostname + "' caused exception: " + ex.getMessage(), ex);
		}

		Collection<MonitorMeasure> measures = env.getMonitorMeasures();
		boolean partial = false;
		for (MonitorMeasure measure : measures) {			
			try {
				
				addMetric(measure);	
				
			} catch (PerformanceCounterException ex) {
				
				partial = true;
				if (log.isLoggable(Level.WARNING)) {					
					String objectName = measure.getParameter(MEASURE_CONFIG_STRING_OBJECT_NAME);
					String counterName = measure.getParameter(MEASURE_CONFIG_STRING_COUNTER_NAME);
					log.log(Level.WARNING, "registering of perfmon measure " + objectName +"/"+counterName +"("+instance+")" +"caused an exception", ex);				
				}				
			}
		}
		if (partial) {
			return new Status(Status.StatusCode.PartialSuccess, "Initializing performance queries caused errors", perfmon.getDetailedErrors());			
		}
		return new Status(Status.StatusCode.Success);
	}

	/**
	 * Executes the performance query and sets the measurements for each
	 * MonitorMeasure.
	 *
	 * @param MonitorEnviroment
	 */
	@Override
    public Status execute(MonitorEnvironment env) throws Exception {
		Map<PerformanceMeasureKey, Long> queryResult;
		try {
			// execute the query
			queryResult = perfmon.query();
			
		} catch (PerformanceCounterException ex) {
			return new Status(Status.StatusCode.ErrorInternal, "Executing performance query failed with exception", "Executing performance query failed with exception: " + ex.getMessage(), ex);
		}
		
		boolean failed = true;
		boolean partial = false;
		Collection<MonitorMeasure> measures = env.getMonitorMeasures();
		if (measures.size() == 0) failed = false;
		PerformanceMeasureKey queryPerformanceMeasureKey = new PerformanceMeasureKey("", "", "");
		
		for (MonitorMeasure measure : measures) {
			String objectName = measure.getParameter(MEASURE_CONFIG_STRING_OBJECT_NAME);
			String counterName = measure.getParameter(MEASURE_CONFIG_STRING_COUNTER_NAME);
			String instanceName = instance;
			PerformanceObject perfObject = perfmon.getPerformanceObject(objectName);
			
			if ((perfObject == null) || 
				(perfObject.getCounter(counterName) == null) ||
				(perfObject.getCounter(counterName).getInstance(instanceName) == null) ||
				(!perfObject.getCounter(counterName).getInstance(instanceName).isInitialized()))
				{
				try {
					if ((perfObject != null) && (perfObject.getCounter(counterName) != null) && 
						(perfObject.getCounter(counterName).getInstance(instanceName) != null) && 
						(!perfObject.getCounter(counterName).getInstance(instanceName).isInitialized())) {
						perfmon.addQuery(perfObject);
					} else { 					
						perfObject =  addMetric(measure);
					}

				} catch (PerformanceCounterException ex) {
					partial = true;					
					if (PerformanceObject.doLog(perfObject, counterName, instanceName)) {
						if (log.isLoggable(Level.WARNING)) {												
							log.log(Level.WARNING, "query of perfmon measure " + objectName +"/"+counterName +"("+instanceName+")" +"caused an exception", ex);
						}			
					}
					PerformanceObject.updateDoLogState(perfObject, counterName, instanceName, false);
				}							
			}

			
			queryPerformanceMeasureKey.setObjectName(objectName);
			queryPerformanceMeasureKey.setCounterName(counterName);
			queryPerformanceMeasureKey.setInstanceName(instanceName);			
			Long longValue = queryResult.get(queryPerformanceMeasureKey);
			if (longValue == null) {
				if (PerformanceObject.doLog(perfObject, counterName, instanceName)) {
					if (log.isLoggable(Level.WARNING)) {						
						log.warning("Failed to retrieve measurement for measure " + objectName +"/"+counterName +"("+instanceName+")");
					}
				}				
				PerformanceObject.updateDoLogState(perfObject, counterName, instanceName,false);
				partial = true;
				continue;							
			} 						
			
			double value = longValue.doubleValue();
			if (perfObject.getCounters()[0].getScaleFactor() == PerformanceCounter.SCALE_1000) {
				value *= 0.001;
			}

			if (log.isLoggable(Level.FINE))
				log.fine("Measurement: " + measure + " = " + value);

			measure.setValue(value);
			failed = false;
		}
		if (failed) {
			return new Status(Status.StatusCode.ErrorInternal, "Executing all performance queries caused errors", perfmon.getDetailedErrors());
		}
		if (partial) {			
			return new Status(Status.StatusCode.PartialSuccess, "Executing some performance queries caused errors", perfmon.getDetailedErrors());								
		}
	
		return new Status(Status.StatusCode.Success);
	}

	/**
	 * Clear the metric map and performance query and disconnect the PerformanceMonitor.
	 * 
	 * @param MonitorEnvironment
	 */
	@Override
    public void teardown(MonitorEnvironment env) throws Exception {
		try {
			perfmon.clearQuery();
		} catch (PerformanceCounterException ex) {
			if (log.isLoggable(Level.WARNING))
				log.log(Level.WARNING, "Failed to clear performance query", ex);
		}
		try {
			perfmon.disconnect();
		} finally {
			perfmon = null;
		}
	}
}
