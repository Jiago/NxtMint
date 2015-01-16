NxtMint
=======

NxtMint mints currencies defined by the Nxt Monetary System.  A single currency can be minted as specified by the NxtMint configuration file.  The minting algorithm is executing using one or more CPU threads or GPU instances.  Newly-minted coins will be added to the account specified in the configuration file.     

The NRS node used to create the mint transactions must accept API connections.  This is done by specifying nxt.apiServerPort, nxt.apiServerHost and nxt.allowedBotHosts in nxt.properties.  The account secret phrase is not sent to the Nxt server since the mint transactions are created and signed locally.     

Aparapi and OpenCL are used to mint using the GPU and are not needed if you are using just the CPU.  You will need to download Aparapi from http://code.google.com/p/aparapi and obtain OpenCL from your graphics card vendor (OpenCL may be automatically installed as part of the graphics card drive installation).  The runtime libraries (aparapi and opencl) need to be installed in either the program execution path or the Java library path.  The program execution path is specified by the PATH environment variable and the Java library path is specified on the java command line as -Djava.library.path=path-name.  In addition, Java on Windows will check the current directory for runtime libraries so you can just copy the runtime libraries to the directory containing NxtMint.jar.  The Aparapi and OpenCL libraries must match the JVM (32-bit or 64-bit) being used to run NxtMint.  For example, NVIDIA provides OpenCL.dll and OpenCL64.dll on Windows in C:\Program Files\NVIDIA Corporation\OpenCL.  Aparapi is looking for OpenCL.dll, so you need to rename OpenCL64.dll to OpenCL.dll in order to use the 64-bit JVM.   


Build
=====

I use the Netbeans IDE but any build environment with Maven and the Java compiler available should work.  The documentation is generated from the source code using javadoc.

Here are the steps for a manual build.  You will need to install Maven 3 and Java SE Development Kit 8 if you don't already have them.

  - Install the NxtCore project.    
  - Install aparapi.jar in the local Maven repository with groupId=com.amd, artifactId=aparapi, version=2013.01.23      
  - Create the executable: mvn clean package    
  - [Optional] Create the documentation: mvn javadoc:javadoc    
  - [Optional] Copy target/NxtMint-v.r.m.jar and lib/* to wherever you want to store the executables.    
  - Create a shortcut to start NxtMint using java.exe for a command window or javaw.exe for GUI only.    


Runtime Options
===============

The following command-line options can be specified using -Dname=value

  - nxt.datadir=directory-path		
    Specifies the application data directory. Application data will be stored in a system-specific directory if this option is omitted:		
	    - Linux: user-home/.NxtMint	    
		- Mac: user-home/Library/Application Support/NxtMint    
		- Windows: user-home\AppData\Roaming\NxtMint	    
	
  - java.util.logging.config.file=file-path		
    Specifies the logger configuration file. The logger properties will be read from 'logging.properties' in the application data directory. If this file is not found, the 'java.util.logging.config.file' system property will be used to locate the logger configuration file. If this property is not defined, the logger properties will be obtained from jre/lib/logging.properties.
	
    JDK FINE corresponds to the SLF4J DEBUG level	
	JDK INFO corresponds to the SLF4J INFO level	
	JDK WARNING corresponds to the SLF4J WARN level		
	JDK SEVERE corresponds to the SLF4J ERROR level		

The following configuration options can be specified in NxtMint.conf.  This file is required and must be in the application directory.	

  - connect=host    
    Specifies the NRS host name and defaults to 'localhost'		
	
  - apiPort=port		
	Specifies the NRS API port and defaults to 7876.    
    
  - secretPhrase=phrase     
    Specifies the account secret phrase and must be specified.  The secret phrase will not be sent to the NRS server.   
    
  - currency=code      
    Specifies the code for the currency to be minted.       

  - units=count     
    Specifies the number of units to generate for each hash round and defaults to 1.  The hash difficulty increases as the number of units increases but the transaction fee is 1 Nxt no matter how many units are generated.  Thus you want to increase units as much as possible to reduce the cost of minting the currency but don't set it so high that you don't mint anything during a session.  The count can be specified as an integer value or as a decimal value with a maximum number of digits following the decimal point as defined for the currency.        
    
  - cpuThreads=count       
    Specifies the number of CPU threads to be used and defaults to 1.  Specifying a thread count greater than the number of CPU processors will not improve minting since the mint algorithms are CPU-intensive and will drive each process to 100% utilization.  Decrease the thread count if your computer becomes too hot or system response degrades significantly.     
    
  - gpuIntensity=count    
    Specifies the GPU computation intensity and defaults to 0.  You must install Aparapi (https://code.google.com/p/aparapi) and your graphics card must support OpenCL in order to use the GPU.  The highest intensity that can be specified is dependent on the amount of memory available on the graphics card.  You will need to try different values to determine an acceptable rate.  Specifying too large a value will result in performance degradation and GPU memory errors.  Start with an initial value of 10 and raise or lower needed.   
    
  - enableGUI=true|false      
    Specifies whether or not to enable the GUI and defaults to true.  Disabling the GUI allows NxtMint to run in headless environments such as a disconnected service.      
	
    
Sample Windows shortcut:	

	javaw.exe -Xmx256m -jar \Nxt\NxtMint-1.0.0.jar   


If you are using Aparapi and OpenCL, the dynamic libraries for these products must be in the system path or in the Java library path.  The Java library path is specified using -Djava.library.path=path-name.  For example, if the libraries are installed in \Nxt\lib, the Windows shortcut would look like this:

    javaw.exe -Xmx256m -Djava.library.path=\Nxt\lib -jar \Nxt\NxtMint-1.0.0.jar 
    
    