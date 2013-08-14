Router (Shortest Path Routing Algorithm)

- Please use make to compile to program before execution

NAME
	router

SYNOPSIS
	./router <router_id> <nse_host> <nse_port> <router_port>

DESCRIPTION
	Router will collect link state(LS) PDU from the Network State Emulator(nse) that will deliver the circuit database of the router and furthur transfer these LS PDU to other router. During the process the router will be able to discover all reachable routers with its topology database. Then by using Dijkstra Algorithm to determine the shortest path, each router will keep a latest routing information base(RIB) that allows router to find the shortest path to another router. Log files will be created in the same directory with name routerX where X is the id of the router.

ASIDE
-Log Files:
         Log file records all packets send and receive by the router, including INIT, HELLO and LS PDU packets. It also generate a new topology database and RIB when a new link from a router is discovered.

-Program termination:
	 Notice due to my implementation I did not ignore sending duplicate packets but instead what I did is to do a check when a packet received whether the link is already in the topology database. If it does, I will not resend to neighbour and otherwise. Thus the program will not terminate but will pause until a new router or link is added in.
	
ENVIRONMENT
	Compiled and ran under linux002, using GNU Make 3.81

EXAMPLE:
	yylin@mef-linux002:~/cs456/a3$ ./nse-linux386 localhost 9999
	yylin@mef-linux002:~/cs456/a3$ ./router 1 9999 9991
	yylin@mef-linux002:~/cs456/a3$ ./router 2 9999 9992
	yylin@mef-linux002:~/cs456/a3$ ./router 3 9999 9993
	yylin@mef-linux002:~/cs456/a3$ ./router 4 9999 9994
	yylin@mef-linux002:~/cs456/a3$ ./router 5 9999 9995	

	I will not show all log but the final topology database and final RIB

	*****************
	** router1.log **
	*****************

	*****************
	** router2.log **
	*****************

	*****************
	** router3.log **
	*****************

	*****************
	** router4.log **
	*****************

	*****************
	** router5.log **
	*****************
