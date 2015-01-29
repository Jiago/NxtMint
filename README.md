NxtMint
=======

NxtMint mints currencies defined by the Nxt Monetary System.  A single currency can be minted as specified by the NxtMint configuration file.  The minting algorithm is executed using one or more CPU threads or GPU work items.  Newly-minted coins will be added to the account specified in the configuration file.     

The NRS node used to create the mint transactions must accept API connections.  This is done by specifying nxt.apiServerPort, nxt.apiServerHost and nxt.allowedBotHosts in nxt.properties.  The account secret phrase is not sent to the Nxt server since the mint transactions are created and signed locally.  

NxtMint requires the Java 8 runtime since it uses language features that are not available in earlier versions of Java.   

OpenCL is used to mint using the GPU and is not needed if you are using just the CPU.  You will need to obtain OpenCL from your graphics card vendor (OpenCL may be automatically installed as part of the graphics card driver installation).


Installation
============

Application data will be stored in a system-specific directory unless you specify your own directory using the -Dnxt.datadir command-line option.  The default directories are:

	- Linux: user-home/.NxtMint	    
	- Mac: user-home/Library/Application Support/NxtMint    
	- Windows: user-home\AppData\Roaming\NxtMint	    
        
Perform the following steps to install NxtMint on your system.

    - Install the Java 8 runtime if you do not already have it installed.     
    - Download the latest version from https://github.com/ScripterRon/NxtMint/releases.       
    - Extract the files from the archive in a directory of your choice.   
    - Copy sample.NxtMint.conf to the application data directory and rename it to NxtMint.conf.  Edit the file to specify your desired NRS server, your secret passphrase (the passphrase will not be sent to the server) and the desired number of CPU threads and/or GPU intensity.  If you are using the GPU, start with gpuIntensity=1 and gpuDevice=0,32,32 and then increase the values until either there is no further improvement in the hash rate or your graphics card begins to overheat.  Your device driver will fail to load the OpenCL kernel if you exceed the available resources (this is especially true for Scrypt since it has a large memory requirement).  The global size (work group size * work group count) determines how much storage is required.    
    - Copy sample.logging.properties to the application data directory and rename it to logging.properties.  Edit the log file name if you want to place it somewhere other than the temporary directory for your userid.     
    - Install OpenCL if you want to use the GPU for mining.  The OpenCL runtime library must be in PATH (Windows) or LD_LIBRARY_PATH (Linux).
    - Rename sample.mint.sh to mint.sh and sample.mint.bat to mint.bat.  Edit the appropriate file to fit your needs. 


Build
=====

You can build NxtMint from the source code if you do not want to use the packaged release files.  I use the Netbeans IDE but any build environment with Maven and the Java compiler available should work.  The documentation is generated from the source code using javadoc.

Here are the steps for a manual build.  You will need to install Maven 3 and Java SE Development Kit 8 if you don't already have them.

  - Download and build the NxtCore project (https://github.com/ScripterRon/NxtCore)
  - Create the executable: mvn clean package    
  - [Optional] Create the documentation: mvn javadoc:javadoc    
  - [Optional] Copy target/NxtMint-v.r.m.jar and lib/* to wherever you want to store the executables.    


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
    Specifies the number of CPU threads to be used and defaults to 1.  Specifying a thread count greater than the number of CPU processors will not improve minting since the mint algorithms are CPU-intensive and will drive each processor to 100% utilization.  Decrease the thread count if your computer becomes too hot or system response degrades significantly.  No CPU threads will be used if cpuThreads is 0.     
    
  - gpuIntensity=count    
    Specifies the GPU computation intensity and defaults to 0.  Your graphics card must support OpenCL in order to use the GPU.  You will need to try different values to determine an acceptable hash rate.  Specifying too large a value will result in performance degradation and GPU memory errors.  Start with an initial value of 1 and raise or lower needed.  A GPU will not be used if gpuIntensity is 0.   
    
  - gpuDevice=index,wsize,gcount
    Specifies the GPU device number (0, 1, 2, ...), the work group size and the work group count.  The first GPU device will be used if this parameter is omitted.  This parameter can be repeated to use multiple GPU devices.  The GPU devices that are available are listed when NxtMint starts if a non-zero value for gpuIntensity is specified.  

    The work group size specifies the number of work items per work group and defaults to 32.  Performance can sometimes be improved by setting the work group size to the number of cores in a compute unit.  You can determine this value by dividing the number of cores on the card by the number of compute units.  In addition, each card has a preferred work item multiple.  For example, if the preferred multiple is 32, work item sizes that are a multiple of 32 will often give better performance (unless there are resource limitations or memory contention).    
    
    The work group count specifies the number of work groups per kernel execution.  If this parameter is omitted, the number of work groups is determined by the gpuIntensity value.  Performance can sometimes be improved by setting the work group count to the number of compute units for the device.  Multiple kernel execution passes will be performed if the work group count is smaller than the number required by the gpuIntensity.
    
  - enableGUI=true|false      
    Specifies whether or not to enable the GUI and defaults to true.  Disabling the GUI allows NxtMint to run in headless environments such as a disconnected service.      
	

