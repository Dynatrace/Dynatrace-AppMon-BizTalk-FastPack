package com.dynatrace.diagnostics.plugin.perflib.perfmon.internal;

import java.net.URL;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.eclipse.core.runtime.FileLocator;
import com.dynatrace.diagnostics.plugin.perflib.perfmon.PerformanceCounterException;

/**
 * The class manage the bridge between the java- and native-side. We load the
 * PerformanceCounters.dll which includes some functions for performance
 * counters and net use.
 * 
 * @author martin.kremenak
 * 
 */

class PerformanceCounters {

	private static final String LIB_PERFORMANCE_COUNTERS_32_DLL = "/res/PerformanceCounters.dll";
	private static final String LIB_PERFORMANCE_COUNTERS_64_DLL = "/res/PerformanceCounters-x64.dll";
	
	private static final Logger log = Logger.getLogger(PerformanceCounters.class.getName());

	public static final int FORMAT_NOSCALE = 0x00001400;
	public static final int FORMAT_SCALE1000 = 0x00002400;

	/**
	 * try to load the PerformanceCounters.dll
	 */
	static {	
		// property "sun.arch.data.model" is available on IBM JVMs to.
		String bitWidth = System.getProperty("sun.arch.data.model");
		
		boolean is64Bit = false;
		if (bitWidth == null) {
			if (log.isLoggable(Level.WARNING)) {
				log.log(Level.WARNING, "32/64 bit environment determination failed, assuming 32 bit.");										
			}
		} else {
			is64Bit = (bitWidth.contains("64"));
		}
		URL url = null;
		String dllUri = null;
		if (is64Bit) { 
			dllUri = LIB_PERFORMANCE_COUNTERS_64_DLL;
		} else {
			dllUri = LIB_PERFORMANCE_COUNTERS_32_DLL;			
		}
		url = PerformanceCounters.class.getResource(dllUri);	
		try {
			url = FileLocator.toFileURL(url);

			// System.load(new
			// File("./lib/PerformanceCounters.dll").getAbsolutePath());
			System.load(url.getFile());			
		} catch (Throwable e) {
			if (e != null) {
				if (log.isLoggable(Level.WARNING)) {
					log.log(Level.WARNING, "Exception occured during setup of performance counters using dll " + dllUri, e);										
				}
			}
			throw new RuntimeException(e);
		}
	}

	/**
	 * Initialization method for performance counters. Must be called before you
	 * use the other performance counter methods. Returns a long which represent
	 * a pointer to our native object.
	 * 
	 * @return long pointer which represent a object reference to the native
	 *         object
	 * @throws PerformanceCounterException
	 */
	public static native long initialize() throws PerformanceCounterException;

	/**
	 * The reference-parameter is a long which represent the reference to the
	 * native object which have you create with the initialize.
	 * 
	 * @param reference
	 * @throws PerformanceCounterException
	 */
	public static native void uninitialize(long reference) throws PerformanceCounterException;

	/**
	 * clears the performanceCounterMap
	 * 
	 * @param reference
	 * @return return if function call was ok else false
	 * @throws PerformanceCounterException
	 */
	public static native boolean clearPerformanceCounterMap(long reference) throws PerformanceCounterException;

	/**
	 * Get a string array with all object names
	 * 
	 * @param reference
	 *            reference to the native object
	 * @return string array which contains the objects (e.g. "Processor")
	 * @throws PerformanceCounterException
	 */
	public static native String[] getObjects(long reference, String hostName) throws PerformanceCounterException;

	/**
	 * Get a string array with all Instance names
	 * 
	 * @param reference
	 * @param objectName
	 * @param hostName
	 * @return
	 * @throws PerformanceCounterException
	 */
	public static native String[] getInstances(long reference, String objectName, String hostName)
			throws PerformanceCounterException;

	/**
	 * the method must be called for every getValue call if want to get the
	 * actual value
	 * 
	 * @param reference
	 *            reference to the native object
	 * @return return true we reqeruy was called successful
	 * @throws PerformanceCounterException
	 */
	public static native boolean requery(long reference) throws PerformanceCounterException;

	/**
	 * check if the counters exists else the method create them. If the counter
	 * does not exists we get a invalid data Exception. The method can throw a
	 * PerformanceCounterException e.g. the counter does not exists or an
	 * InvalidPerformanceCounterException if a invalid data was delivered.
	 * 
	 * @param reference
	 *            "pointer" to our native object from the initialize method.
	 * @param hostName
	 *            name of the host where counters be requested
	 * @param objectName
	 *            object name of the counter e.g. "Processor"
	 * @param counterName
	 *            counter name of the performanceCounter e.g. "% Processor Time"
	 * @param instanceName
	 *            instance name of the counter e.g. "_Total"
	 * @param format
	 *            scale of the value * 1000 if counter returns a percent unit
	 *            --> /1000 for correct value
	 * @return return the value as long for percent it is scaled with the factor
	 *         1000
	 * @throws PerformanceCounterException
	 */
	public static native long getValue(long reference, String hostName, String objectName, String counterName,
			String instanceName, int format) throws PerformanceCounterException;

	/**
	 * Get the instance name of the actual instance
	 * 
	 * @param reference
	 *            reference to native object
	 * @return return instance name as string
	 * @throws PerformanceCounterException
	 */
	public static native String getInstanceName(long reference) throws PerformanceCounterException;

	/**
	 * Establish a net use connection.
	 * 
	 * @param serverName
	 *            name of the host on which you want to connect e.g.
	 *            "fileserver"
	 * @param domainName
	 *            name of the domain
	 * @param userName
	 *            username of user that exists on the host
	 * @param password
	 *            the password to the user
	 * @param reference
	 *            reference to the native object
	 * @return true if connect was successful else false
	 */
	public static native boolean addNetUse(String serverName, String domainName, String userName, String password,
			long reference);

	/**
	 * delete the connection to the server
	 * 
	 * @param serverName
	 *            host name on which you want to kill the connection
	 * @param reference
	 *            reference to the native object which was created with the
	 *            initNetUse method
	 * @return return true disconnect was successful else false
	 */
	public static native boolean delNetUse(String serverName, long reference);

	/**
	 * initialization of the native net use object in the performanceCounter.dll
	 * 
	 * @return a reference to the intern native object as long
	 */
	public static native long initNetUse();

	/**
	 * delete the native object
	 * 
	 * @param reference
	 *            reference of the native object
	 */
	public static native void uninitNetUse(long reference);

	/**
	 * check if a connection with the specified user credentials are established
	 * 
	 * @param serverName
	 *            name of the host where are the connection should be
	 * @param domainName
	 *            domain of the authentication
	 * @param userName
	 *            user name of the authentication
	 * @param refernce
	 *            reference to the netUse native Object
	 * @return if connection is established the function return true else false
	 */
	public static native boolean establishedConnection(String serverName, String domainName, String userName,
			long refernce);			
}
