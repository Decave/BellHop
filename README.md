# Credentials

David C. Vernet
dcv2105
CSEE4119 -- Computer Networks
Spring 2014

## Application Purpose
The purpose of this application (aside from being great practice, of course), is to provide a quick, basic UDP 
driven BellmanFord P2P file sharing application. The routing algorithm is at its heart a Distance Vector algorithm, 
and uses the classical distributed, iterative approach to routing as is the case with BellmanFord given by:
	d_{x}(y) = min_{v} {(c(x, v) + d_v(y)}

More features of the application will be discussed in the remainder of this README file.


## Usage
To run the application, enter the directory with the Makefile and execute the following series of commands:

	$ make
	$ ./bfclient <config_file>,

Running `make` will compile the Java classes you need to actually run the program, and will also create an executable
file called `bfclient` that contains a short Bash script to start the application with a parameter. As just mentioned, 
note that you must be running in a Bash shell (or another Unix terminal with the same syntax for our case) in order to 
properly execute the above commands and make the program work correctly. You can also run:
	$ make clean

If you would like to clean up the old .class files and get rid of bfclient (though it is also removed and remade each 
time make is called as well). Remark that for the application to start, you must provide the name of a config file that 
is located in the same directory as the Makefile, and with the following syntax:
	localport timeout file_chunk_to_transfer file_sequence_number
	ipaddress1:port1 weight1
	[ipaddress2:port2 weight2]
	[...]
In this configuration file, localport is the port on which your Client instance will be listening for incoming UDP requests,
timeout is the number of seconds before a __ROUTE-UPDATE__ message is sent to all of your neighboring nodes, and 1/3 the 
number of seconds before a neighbor is dropped if you do not hear a __ROUTE-UPDATE__ message from them in that time.
The file_chunk_to_transfer and file_sequence_number parameters represent the name of the file you wish to share, and the 
sequence number / ordering of that file respectively. A peer must receive both files in order for either to be downloaded to 
his or her machine. Note as well that file_chunk_to_transfer and file_sequence_number are not strictly necessary to run the 
program, though of course, you will be unable to send a file (though you can still send routing-related messages).


## Code Description
The heart of the code is in the Client.java class. This is where the application listens on the port given in the configuration
file, where routing messages are defined and exhanged, where routing related data structures are maintained, and where the logic of data 
exchange is performed. The ClientDatagramSender.java class is responsible for keeping track of a DatagramSocket for each neighbor, and the
TimerTask.java classes are responsible for giving the Timers tasks that should be performed when a timer expires (in the application, the two events
that occur are that a __ROUTE-UPDATE__ message is sent to each neighbor who does not have an infinite weight from the local client, and a
neighbor is dropped if they are not heard from within a given period of time. Finally, the ClientReaderThread.java class is 
responsible for listening on stdIn for terminal inputs, and responds to several commands, listed below:

	* TRANSFER <ip address> <port Num>
		- This command sends the data file and sequence number specified in the configuration file.
	* SHOWRT
		- This command prints the local Client's current routing table
	* LINKDOWN <ip address> <port num>
		- This command sets the weight of a neighbor (in your routing table) to infinity, essentially removing them as a neighbor. This is undone by the magic of the next command:
	* LINKUP <ip address> <port num> <weight>
		- This command renews a previously destroyed link with the weight given at the command line. After each of the above methods are called, all of your neighbors are notified of the change so they can update their distance vectors and routing tables accordingly.
	* CLOSE
		- This command does exactly what it claims. Exit the program, without warning any of your peers, with an exit status of 1.

## Protocol Description
It wouldn't be a routing application without a handy do-it-yourself routing algorithm. There are four types of messages in the application:
1. __TRANSFER__
2. __ROUTE-UPDATE__
3. __LINK-DOWN__
4. __LINK-UP__

Each of the above messages has a header section and a body section, with some variances in all of the messages. All of the messages use 
regex matching and String splitting to allow the application to know where a section of the message starts and stops, where data is contained 
within the message, etc.

* The __TRANSFER__ message has the following header field syntax:
__TRANSFER__|endPathRecipient|nextHop|localClientInformation|Chunk sequence number#[body of message]
As you can see, it's fairly simple to parse the header, because we can simply 'split' our String around the '#' character, and then 'split'
our header around the | characters to pull out the values we need. In this case, we can see which destination the __TRANSFER__ file is intended 
for, and respond accordingly.

The body of __TRANSFER__ message also has an interesting syntax, with hosts who forward a __TRANSFER__ message appending their names to the message
in a '@HostIP:HostPort' format so that a post-path success message can be printed after the message reaches its destination to see who helped to 
move the package.

* The __ROUTE-UPDATE__ message has the following header field syntax:
__ROUTE-UPDATE__|nextHop|localClientInformation#[Distance Vector string]
In similar fashion to __TRANSFER__, we use special syntax and punctuation to be able to pull apart a string later and analyze it as a command.
In the body of the __ROUTE-UPDATE__ message, we have a String representation of a distance vector, that is transformed back into a TreeMap and 
then used to update a Clients' neighbors distance vectors and routing tables.

* The __LINK-DOWN__ command has very simple syntax:
__LINK-DOWN__|localClientInformation#
As you can see, our pattern is simple, and effective. There's nothing tricky about this one -- in this case, the only information your neighbor needs 
is your IPAddress:Port number tag, and the fact that you want to tear a link down. Remember that until you send a LINKUP command, this neighbor will
be unavailable for routing from your machine.

* The __LINK-UP__ command also has very simple syntax:
__LINK-UP__|localClientInformation|weight#
Again, the protocol is straightforward, consistent, and easy to use! Remember that in this case, when you put the link back up, your neighbor will
once again be accessible for routing tasks.


## Development Environment

I developed this application using Java 1.6, and the openjdk-6-jre in ensuring compliance with the stability requirements of testing. As mentioned above,
the Makefile makes use of the Bash shell, though it should be straightforward to compile the commnad without `make` should the need arise. I developed
my application using Git, and in a Debian OS.


### Supplementary application features

The only supplementary feature implemented was the SHOWDV command mentioned above, that allows a user to view his or her current distance vector in 
state-table form. As a reminder, to use this feature, simply type

	`$ SHOWDV`

at the terminal window after you have connected a Client instance to the application.


## Sample Commands

As above, please consider a '$' character to represent a Bash terminal. Begin by navigating to the root of the application:

			- client/
	dcv2105_Java.zip- configThreeNeighbors1
			- configThreeNeighbors2
			- configThreeNeighbors3
			- Makefile

Once you've reached this directory, you can execute the following commands to open up a series of Clients in the application:

	$ make
	$ ./bfclient configThreeNeighbors1
	$ ./bfclient configThreeNeighbors2
	$ ./bfclient configThreeNeighbors3

Let's assume we're at the terminal for configThreeNeighbors1, who has the following configuration file:

4400 60 chunk1 1
74.73.139.233:7881 1.4
74.73.139.231:6661 2.3
74.73.139.228:3131 10.0
	
If you were to type:
	$ showrt

You would see:

<Current time: 18:36:05>Distance vector list is:
Destination = 127.0.1.1:4400, Cost = 0.0, Link = (127.0.1.1:4400)
Destination = 74.73.139.228:3131, Cost = 10.0, Link = (74.73.139.228:3131)
Destination = 74.73.139.231:6661, Cost = 2.3, Link = (74.73.139.231:6661)
Destination = 74.73.139.233:7881, Cost = 1.4, Link = (74.73.139.233:7881)

Similarly, if you were to type:
	$ showdv

You would see:
127.0.1.1:4400's DV: | 127.0.1.1:4400 => 0.0 | 74.73.139.228:3131 => 10.0 | 74.73.139.231:6661 => 2.3 | 74.73.139.233:7881 => 1.4 | 
74.73.139.228:3131's DV: | 127.0.1.1:4400 => Infinity | 74.73.139.228:3131 => Infinity | 74.73.139.231:6661 => Infinity | 74.73.139.233:7881 => Infinity | 
74.73.139.231:6661's DV: | 127.0.1.1:4400 => Infinity | 74.73.139.228:3131 => Infinity | 74.73.139.231:6661 => Infinity | 74.73.139.233:7881 => Infinity | 
74.73.139.233:7881's DV: | 127.0.1.1:4400 => Infinity | 74.73.139.228:3131 => Infinity | 74.73.139.231:6661 => Infinity | 74.73.139.233:7881 => Infinity |

Thank you for using my P2P BellmanFord distribution / routing program. Please feel free to use it and distribute it to your liking, and I am always
happy to be given suggestions. Thank you for your time!

Happy networking!
