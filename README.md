# BizTalk FastPack

![images/logo_biztalk.png](images/logo_biztalk.png =250x)

The dynaTrace FastPack for Microsoft BizTalk Server enables faster performance analysis of BizTalk Environments by providing pre-configured Dashboards, System Profile and Sensor Packs for BizTalk

Find further information in the [dynaTrace community](https://community.dynatrace.com/community/display/DL/BizTalk+FastPack) 


##BizTalk Message Performance
![images/BizTalkPerformance.PNG](images/BizTalkPerformance.PNG)
 
This dashboard provides helps identifying performance problems in the overall message processing infrastructure of BizTalk. It monitors the Windows Performance Counters of BizTalk and alerts on problems that impact the Host throttling such as
•	High message delivery rate
•	High database size
•	High in-process message queue
•	...
This dashboard gives a high-level performance overview of the most critical component in BizTalk - which is the MessageBox, the Message Box Host queue and the message processing. It also shows the message delivery delay such as pending messages, active publishing delay and active send messages.


##BizTalk Documents and Pipelines Dashboard
![images/DocumentsAndPipelines.PNG](images/DocumentsAndPipelines.PNG)

This dashboard provides an insight in the messaging performance counters displaying the processed documents by the BizTalk server. The default pipelines will be detected with a business transaction and will be automatically plotted as well as the different orchestrations being processed by BizTalk server. The slowest Pipelines and Orchestrations as well as the most used ones will filtered and shown in a business transaction dashlet. For correlating infrastructure issues with the amount of processed documents, at the bottom of the dashboard, the active threads, handles, read bytes and written bytes by the host will be displayed. 


##BizTalk Orchestration Engine Dashboard
![images/OrchestrationEngine.PNG](images/OrchestrationEngine.PNG)

The Orchestration Engine dashboard shows rerformance counters about the orchestration health, the transactional scopes and the pending work. A timeline displays the amount of orchestration and transactional metrics per second such as orchestration created, dehydrated, rehydrated, completed and so fort. The Dyhadration threshold, dynadrations in progress as well as the allocated memory and amount of database transactions will be measured and displayed. The slowest Pipelines and Orchestrations as well as the most used ones will filtered and shown in a business transaction dashlet.

##BizTalk Adapters Dashboard
![images/Adapters.PNG](images/Adapters.PNG)
The Adapters dashboard displays the performance counters for the most common adapters such as File, HTTP and SOAP for the received and sent messages. An alert (lock failure / sec) will be triggered when the BizTalk server starts locking messages for their processing. This usually happens when being under load. The handle count and current CPU load helps identifying problems related to the infrastructure.
 
##BizTalk Architect Dashboard
![images/BitTalkArchitect.PNG](images/BitTalkArchitect.PNG)
 
This dashboard provides helps to architecs to identify problems related to:
-	Memory Usage and Garbage Collector Activity
-	Exceptions that are thrown within the BizTalk Orchestration
-	Database and Web Service activity
-	Reponse Time Hotspots among all the transactions and instances of the BizTalk Servers.
-	API Breakdown with the distribution of cpu, sync and wait.
-	Transaction layer breakdown and execution count
The dashboard also shows a performance breakdown into the individual components within BizTalk like BizTalk Core Components, XLANG, Web Services, ...

##BizTalk Deep Analysis Dashboard
![images/DeepAnalysis.PNG](images/DeepAnalysis.PNG)
This dashboards is composed by the following dashlets:
-	Transaction flow 
-	API breakdown
-	Methods
-	Method Hotspots
This dashboard will help developers understand the called methods and indentify the hotspots inside the BizTalk Server such as code running inside the XLANG BTX Engine. The MessageBox is in the transaction flow exposed as an external call with the help of a method sensor. This method is called when the XLANGStore commits it’s work and sends the message to the COM module, which then commits the message in the database. 

