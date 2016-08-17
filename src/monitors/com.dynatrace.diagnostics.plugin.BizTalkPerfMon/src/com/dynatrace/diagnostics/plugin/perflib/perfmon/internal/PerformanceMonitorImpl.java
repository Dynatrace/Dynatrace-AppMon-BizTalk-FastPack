package com.dynatrace.diagnostics.plugin.perflib.perfmon.internal;

import java.io.IOException;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

import com.dynatrace.diagnostics.plugin.perflib.perfmon.InvalidOperationException;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceCounter;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceCounterException;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceMeasureKey;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceMonitor;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceObject;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceCounter.PerformanceInstance;

public class PerformanceMonitorImpl implements PerformanceMonitor {

	private static final Logger log = Logger.getLogger(PerformanceMonitor.class.getName());
	
	private static final String HOSTNAME = "hostname";
	private static final String DOMAIN = "domain";
	private static final String USERNAME = "username";
	private static final String PASSWORD = "password";
	
	private static final String LOCALHOST = "LOCALHOST";
	private static final String LOCAL_IP = "127.0.0.1";
	
	private static final String ERR_ALREADY_CONNECTED = "hostname";
	private static final String ERR_INIT_NETUSE = "cannot initialize NET USE library";	
	private static final String ERR_INIT_PDHLIB = "failed to initialize perfmon library. cause:";
	private static final String ERR_NOT_CONNECTED = "not connected";
	private static final String ERR_CLONE_PERFOBJ = "cannot clone performance object";
	private static final String ERR_CLEAR_QUERY = "failed to clear query";
	private static final String ERR_REQUERY = "querying the performance counters failed";
	private static final String ERR_GET_VALUE = "failed to retrieve value from: ";
	private static final String ERR_DEL_NETUSE = "failed to delete NET USE connection";
	private static final String ERR_UNINIT_NETUSE = "failed to uninitialize NET USE library";
	private static final String ERR_UNINIT_PDHLIB = "failed to uninitialize perfmon library";
	
	
	private long nativeObjectReference = 0;
	private long netUseReference = 0;
	private boolean netUseAdded = false;

	private Map<String, PerformanceObject> queries = new HashMap<String,PerformanceObject>();
	private String hostname = null;

	public PerformanceMonitorImpl() {
	}

	@Override
	public void connect(String hostname) throws PerformanceCounterException,
			InvalidOperationException, IOException {
		if (hostname == null)
			throw new NullPointerException(HOSTNAME);

		if (nativeObjectReference != 0) {
			throw new InvalidOperationException(ERR_ALREADY_CONNECTED);
		}

		this.hostname = fixHostname(hostname);
		if (!this.hostname.equals(LOCAL_IP)) {
			establishNetUseConnection(hostname, null, null, null);	
		}
		initPerfMonLibrary();
	}

	@Override
	public void connect(String hostname, String domain, String username, String password)
			throws IOException, PerformanceCounterException, InvalidOperationException {
		if (hostname == null)
			throw new NullPointerException(HOSTNAME);
		if (domain == null)
			throw new NullPointerException(DOMAIN);
		if (username == null)
			throw new NullPointerException(USERNAME);
		if (password == null)
			throw new NullPointerException(PASSWORD);

		if (netUseReference != 0)
			throw new InvalidOperationException(ERR_ALREADY_CONNECTED);

		this.hostname = hostname = fixHostname(hostname);

		establishNetUseConnection(hostname, domain, username, password);

		initPerfMonLibrary();
	}

	private void establishNetUseConnection(String hostname, String domain,
			String username, String password) throws IOException {
		try {
			netUseReference = PerformanceCounters.initNetUse();
		} catch (Exception ex) {
			netUseReference = 0;
			throw new IOException(ERR_INIT_NETUSE);
		}
		netUseAdded = false;
		try {
			netUseAdded = PerformanceCounters.addNetUse(hostname, domain, username, password,
					netUseReference);
			if (!netUseAdded) {
				throw new Exception("Could not establish net use connection to host " + hostname + " in domain " + domain + " for user " + username + ". Please check host address and user credentials.");
			}
		} catch (Exception ex) {
			// uninitialize NET USE if it was initialized
			PerformanceCounters.uninitNetUse(netUseReference);
			netUseReference = 0;
			netUseAdded = false;
			throw new IOException(ex.getMessage());
		} 
	}

	private void initPerfMonLibrary() throws PerformanceCounterException {
		try {
			nativeObjectReference = PerformanceCounters.initialize();
		} catch (Exception ex) {
			nativeObjectReference = 0;
			throw new PerformanceCounterException(ERR_INIT_PDHLIB + ex.getMessage());
		}
	}

	@Override
	public void disconnect() {
		if (netUseReference != 0) {
			if (netUseAdded) {
				// remove connection
				try {
					PerformanceCounters.delNetUse(hostname, netUseReference);
				} catch (Exception ex) {
					// ignore
					if (log.isLoggable(Level.WARNING))
						log.log(Level.WARNING, ERR_DEL_NETUSE, ex);
				} finally {
					netUseAdded = false;
				}
			}
			// uninit NET USE
			try {
				PerformanceCounters.uninitNetUse(netUseReference);
			} catch (Exception ex) {
				// ignore
				if (log.isLoggable(Level.WARNING))
					log.log(Level.WARNING, ERR_UNINIT_NETUSE, ex);
			} finally {
				netUseReference = 0;
			}
		}
		if (nativeObjectReference != 0) {
			try {
				PerformanceCounters.uninitialize(nativeObjectReference);
			} catch (Exception ex) {
				// ignore
				if (log.isLoggable(Level.WARNING))
					log.log(Level.WARNING, ERR_UNINIT_PDHLIB, ex);
			} finally {
				nativeObjectReference = 0;
			}
		}
	}

	private String fixHostname(String hostname) {
		// under xp localhost make troubles --> switch to 127.0.0.1
		if (hostname.equalsIgnoreCase(LOCALHOST)) {
			return LOCAL_IP;
		}
		return hostname;
	}

	@Override
	public void addQuery(PerformanceObject object) throws PerformanceCounterException,
			InvalidOperationException {
		if (nativeObjectReference == 0) {
			throw new InvalidOperationException(ERR_NOT_CONNECTED);
		}
		if (object == null) {
			throw new NullPointerException();
		}

		// clone the object
		try {
			object = (PerformanceObject) object.clone();
		} catch (CloneNotSupportedException e) {
			throw new PerformanceCounterException(ERR_CLONE_PERFOBJ);
		}

		// check the object
		PerformanceObject sameObject = queries.get(object.getName());
		if (sameObject == null) {
			// we haven't found a query object with the same name --> add the
			// new object
			addQueryObject(object);
		} else {
			// check the counters
			for (PerformanceCounter counter : object.getCounters()) {
				PerformanceCounter sameCounter = sameObject.getCounter(counter.getName());			
				if (sameCounter == null) {
					// we haven't found a query counter with the same name -->
					// add the new counter
					addQueryCounter(sameObject, counter);
				} else {
					// check the instances
					for (PerformanceInstance instance : counter.getInstances()) {
						PerformanceInstance sameInstance = sameCounter.getInstance(instance.getInstanceName());
						if (sameInstance == null) {
							// we haven't found a query instance with the same
							// name --> add the new instance
							addQueryInstance(sameObject, sameCounter, instance);
						}
					}
				}
			}
		}
	}

	private void addQueryObject(PerformanceObject object) throws PerformanceCounterException {
		for (PerformanceCounter counter : object.getCounters()) {
			for (PerformanceInstance instance : counter.getInstances()) {
				addNativeQuery(object, counter, instance);
			}
		}
		queries.put(object.getName(), object);
	}

	private void addQueryCounter(PerformanceObject object, PerformanceCounter counter)
			throws PerformanceCounterException {
		for (PerformanceInstance instance : counter.getInstances()) {
			addNativeQuery(object, counter, instance);
		}
		object.addCounter(counter);
	}

	private void addQueryInstance(PerformanceObject object, PerformanceCounter counter,
			PerformanceInstance instance) throws PerformanceCounterException {
		addNativeQuery(object, counter, instance);
		counter.addInstance(instance);
	}

	private void addNativeQuery(PerformanceObject object, PerformanceCounter counter,
			PerformanceInstance instance) throws PerformanceCounterException {
		try {
			if (!instance.isInitialized()) {
				PerformanceCounters.getValue(nativeObjectReference, hostname, object.getName(), counter
					.getName(), instance.getInstanceName(), counter.getScaleFactor());
				instance.setInitialized(true);
				instance.setLastErrorMessage(null);
			}
		} catch (InvalidPerformanceCounterException ex) {			
			// indicates temporarily invalid counter, e.g. delta counter without reference value to calculate delta. Can be ignored. 
		} catch (PerformanceCounterException ex) {
			instance.setInitialized(false);
			instance.setLastErrorMessage(ex.getMessage());			
		}
	}

	@Override
	public void clearQuery() throws PerformanceCounterException, InvalidOperationException {
		if (nativeObjectReference == 0) {
			throw new InvalidOperationException(ERR_NOT_CONNECTED);
		}
		try {
			if (!PerformanceCounters.clearPerformanceCounterMap(nativeObjectReference)) {
				throw new Exception();
			}
			queries.clear();
		} catch (Exception ex) {
			throw new PerformanceCounterException(ERR_CLEAR_QUERY);
		}
	}

	@Override
	public Collection<PerformanceObject> getPerformanceIdentifiers()
			throws PerformanceCounterException {
		if (nativeObjectReference == 0) {
			throw new InvalidOperationException(ERR_NOT_CONNECTED);
		}
		// TODO: implement: build a Collection of PerformanceObject trees
		// PerformanceCounters.getObjects(nativeObjectReference, hostname);
		// TODO PerformanceCounters.getCounters???
		// PerformanceCounters.getInstances(nativeObjectReference, objectName, hostname);
		throw new UnsupportedOperationException();
	}

	@Override
	public Map<PerformanceMeasureKey, Long> query() throws PerformanceCounterException, InvalidOperationException {
		if (nativeObjectReference == 0) {
			throw new InvalidOperationException(ERR_NOT_CONNECTED);
		}
		try {
			if (!PerformanceCounters.requery(nativeObjectReference)) {
				throw new Exception();
			}
		} catch (Exception ex) {
			throw new PerformanceCounterException(ERR_REQUERY);
		}

		Map<PerformanceMeasureKey, Long> results = new HashMap<PerformanceMeasureKey, Long>();

		for (PerformanceObject object : queries.values()) {
			for (PerformanceCounter counter : object.getCounters()) {
				for (PerformanceInstance instance : counter.getInstances()) {
					try {
						// retrieve the value
						long value = PerformanceCounters.getValue(nativeObjectReference, hostname,
								object.getName(), counter.getName(), instance.getInstanceName(), counter.getScaleFactor());

						PerformanceMeasureKey key = new PerformanceMeasureKey(object.getName(), counter.getName(), instance.getInstanceName());
						// put the value into the results map						
						results.put(key, Long.valueOf(value));
						// successfully queried one measurement, arm logging flag again.
						instance.setDoLog(true);
						instance.setLastErrorMessage(null);
					} catch (InvalidPerformanceCounterException ex) {
						instance.setInitialized(false);
						instance.setLastErrorMessage(ex.getMessage());
						if (instance.isDoLog()) {
							instance.setDoLog(false);
							if (log.isLoggable(Level.WARNING)) {
								log.log(Level.WARNING, ERR_GET_VALUE + object.getName() + ", " + counter.getName() + ", " + instance.getInstanceName(), ex);
							}
						}
					} catch (PerformanceCounterException pce) {
						instance.setInitialized(false);
						instance.setLastErrorMessage(pce.getMessage());
						if (instance.isDoLog()) {
							instance.setDoLog(false);
							if (log.isLoggable(Level.WARNING)) {
								log.log(Level.WARNING, ERR_GET_VALUE + object.getName() + ", " + counter.getName() + ", " + instance.getInstanceName(), pce);
							}
						}
					}
				}
			}
		}
		return results;
	}

	@Override
	public String getDetailedErrors() {
		StringBuffer errorMessage = new StringBuffer();	
		for (PerformanceObject object : queries.values()) {
			if (!object.errorOccurred()) continue;
			errorMessage.append(object.getName()).append("\n");
			for (PerformanceCounter counter : object.getCounters()) {
				if (!counter.errorOccurred()) continue;				
				errorMessage.append("  ").append(counter.getName()).append("\n");
				for (PerformanceInstance instance : counter.getInstances()) {
					if (instance.getLastErrorMessage() != null) {
						errorMessage.append("    ").append(instance.getInstanceName() == null ? "all instances": instance.getInstanceName()).append(":").append(instance.getLastErrorMessage()).append("\n");
					}
				}
			}
		}		
		return errorMessage.toString();
	}

	@Override
	public PerformanceObject getPerformanceObject(String objectName) {
		return queries.get(objectName);
	}
}
