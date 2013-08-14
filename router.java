import java.net.*;
import java.io.*;
import java.util.*;
import java.nio.*;

public class router {
    final int NBR_ROUTER = 5;
    int routerId = 0, nsePort = 0, routerPort = 0;
    int nbrLink = 0;
    InetAddress nseHost = null;
    DatagramSocket nseSocket = null;
    File log = null;
    String logMsg = null;
    neighbour[][] nb = new neighbour[NBR_ROUTER+1][NBR_ROUTER+1]; // 1 if router i+1 is a neighbor, 0 otherwise
    topology_db[] topo_db = new topology_db[NBR_ROUTER+1];	  // topology database
    String routerName = null;

    public router(String argv[]) throws Exception {
	// command prompt
	switch(argv.length) {
	case 4:
	    try {
		routerId = Integer.parseInt(argv[0]);
		routerName = "R" + Integer.toString(routerId);
	    } catch (Exception e) {
		System.err.println("Router Id has to be an integer");
		System.exit(1);
	    }	    
	    nseHost = InetAddress.getByName(argv[1]);
	    try {
		nsePort = Integer.parseInt(argv[2]);
		routerPort = Integer.parseInt(argv[3]);
	    } catch (Exception e) {
		System.err.println("port number has to be integers");
		System.exit(1);
	    }
	    break;
	default:
	    System.err.println("Usage: router <router_id> <nse_host> <nse_port> <router_port>");
	    System.exit(1);
	}

	createLogFile();
	establishSockets();
	ospf();	
    }

    private void createLogFile() throws Exception {
	File curDir = new File(".");
	log = new File(curDir.getCanonicalPath() + "/router" + routerId + ".log");
	if (!log.exists()) log.createNewFile();
    }

    private void establishSockets() throws Exception {
	// establish UDP socket connection to send PDU to NSE
	try {
	    nseSocket = new DatagramSocket(routerPort);
	} catch (IOException e) {
	    System.err.println("Could not establish sending socket");
	    System.exit(1);
	}
    }


    /*************************************************************
     *** Simple Open Shortest Path First Algorithm (OSPF) ********
     *** Shortest path is determined using Dijkstra's Algorithm **
     *************************************************************/

    private void ospf() throws Exception {
	// initialize topology database
	for( int i = 1; i < NBR_ROUTER + 1; i++ ) {
	    Hashtable lc = new Hashtable();
	    topo_db[i] = new topology_db();
	    topo_db[i].linkCost = lc;
	}

	// send INIT packet to nse for circuit database
	packet INIT = packet.createINIT(routerId);
	sendPacket(INIT);
	writeToLog(String.format("%s sends INIT to nse", routerName));

	// collect circuit database from nse
	byte[] receiveData = receivePacket();
	writeToLog(String.format("%s receives INIT from nse", routerName));
	parseCircuitDB(receiveData);

	// wait for HELLO packets first
	Thread receiveHELLO = new Thread(new ReceiveHELLO());
	receiveHELLO.start();
	Thread.sleep(5);

	// send out HELLO packets
	sendHELLO();

	// wait for all HELLO packets received
	receiveHELLO.join();	

	// check if the neighbors are correct
	/*System.out.println("Router: " + Integer.toString(routerId));
	for( int i = 1; i < NBR_ROUTER + 1; i++ ) {
	    System.out.println(nb[i]);
	    }*/

	// wait for LS PDU first
	Thread receiveLSPDU = new Thread(new ReceiveLSPDU());
	receiveLSPDU.start();
	Thread.sleep(5);

	// send out LS PDU to those routers that we received HELLO packets from
	sendLSPDU_HELLO();

	// the rest of the process are done in receiveLSPDU
    }

    // method for sending packets
    private void sendPacket(packet p) throws Exception {
	byte[] sendData = new byte[1024];
	sendData = p.getUDPdata();
	DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length, nseHost, nsePort);
	try {
	    nseSocket.send(sendPacket);
	} catch (Exception e) {
	    System.err.println("Fail to send INIT packet to nse.");
	    System.exit(1);
	}	
    }

    // method for receiving packets
    private byte[] receivePacket() throws Exception {
	byte[] receiveData = new byte[1024];
	DatagramPacket receivePacket = new DatagramPacket(receiveData, receiveData.length);
	try {
	    nseSocket.receive(receivePacket);
	} catch (Exception e) {
	    System.err.println("Error while receiving packet");
	    System.exit(1);
	}
	return receivePacket.getData();
    }


    /* parse UDPdata into circuitDB
       not written in packet class since I need the link_cost class
       when determining the shortest path */
    private void parseCircuitDB(byte[] cdb) throws Exception {
	ByteBuffer buffer = ByteBuffer.wrap(cdb);
	buffer.order(ByteOrder.LITTLE_ENDIAN);
	nbrLink = buffer.getInt();
	for( int i = 1; i < nbrLink + 1; i++ ) {
	    int l = buffer.getInt();
	    int c = buffer.getInt();
	    link_cost a = new link_cost(l, c);
	    topo_db[routerId].linkCost.put(l, a);
	}	
    }

    // method that only send HELLO packet
    private void sendHELLO() throws Exception {
	for( Enumeration e = topo_db[routerId].linkCost.keys(); e.hasMoreElements();) {
	    int i = (Integer)e.nextElement();
	    link_cost lc = (link_cost)topo_db[routerId].linkCost.get(i);
	    packet HELLO = packet.createHELLO(routerId, lc.link);
	    sendPacket(HELLO);
	    // change via link
	    logMsg = String.format("%s sends one HELLO packet to neighbour router via link %d", routerName, lc.link);
	    writeToLog(logMsg);
	}	
    }

    // this method sends the LS PDU to those routers that we have received HELLO packets
    private void sendLSPDU_HELLO() throws Exception {
	for( int i = 1; i < NBR_ROUTER + 1; i++ ) {
	    if( nb[routerId][i].isNeighbour ) {
		for( Enumeration e = topo_db[routerId].linkCost.keys(); e.hasMoreElements(); ) {
		    int j = (Integer)e.nextElement();
		    link_cost lc = (link_cost)topo_db[routerId].linkCost.get(j);
		    int via = nb[routerId][i].nLink.link;
		    packet LSPDU = packet.createLSPDU(routerId, routerId, lc.link, lc.cost, via);
		    sendPacket(LSPDU);
		    logMsg = String.format("%s sends to R%d via link %d that R%d has a link with id %d and cost %d", routerName, i, via, routerId, lc.link, lc.cost);
		    writeToLog(logMsg);
		}
	    }
	}
    }

    // this method resends the LS PDU to its neighbour 
    private void sendLSPDU(packet p) throws Exception {
	for( int i = 1; i < NBR_ROUTER + 1; i++ ) {
	    if( nb[routerId][i].isNeighbour && i != p.getSender()) { // do not send to sender
		int via = nb[routerId][i].nLink.link;
		packet LSPDU = packet.createLSPDU(routerId, p.getRouterId(), p.getLinkId(), p.getCost(), via);
		sendPacket(LSPDU);
		logMsg = String.format("%s sends to R%d via link %d that R%d has a link with id %d and cost %d", routerName, i, via, LSPDU.getRouterId(), LSPDU.getLinkId(), LSPDU.getCost());
		writeToLog(logMsg);
	    }
	}
    }

    // this method chekcs if the topology database needs to be updated
    private void updateTopologyDB(packet p) throws Exception {
	int sId = p.getSender();
	int rId = p.getRouterId();
	int sVia = p.getVia();
	int lId = p.getLinkId();
	link_cost lc = new link_cost(p.getLinkId(), p.getCost());
	boolean update = false;

	// first update the path of each router in topology database
	if (!topo_db[rId].linkCost.containsKey(lId)) {
	    topo_db[rId].linkCost.put(lId, lc);
	    update = true;
	    logTopoDB();
	    Thread.sleep(5);	//  wait for the topology database to be logged
	}

	// update neighbour information
	/* if the router id from the LS PDU is currently not a neighbour of the sender,
	   check if it is, this helps us to get the neighbour information of other routers */
	if (!nb[sId][rId].isNeighbour && topo_db[sId].linkCost.containsKey(lId)) {
	    nb[sId][rId].isNeighbour = true;
	    nb[sId][rId].nLink = lc;
	    update = true;
	}
	
	// if any update is applied to topology database, udpate RIB
	if (update) {
	    updateRIB();
	    Thread.sleep(5);	// wait for RIB to be updated
	    sendLSPDU(p);	// send LS PDU to neighbours, exclude the sender
	}
    }
    
    private void logTopoDB() throws Exception {
	writeToLog("# Topology Database");
	for( int i = 1; i < NBR_ROUTER + 1; i++ ) {
	    logMsg = String.format("%s -> R%d nbr link %d", routerName, i, topo_db[i].linkCost.size());
	    writeToLog(logMsg);
	    for( Enumeration e = topo_db[i].linkCost.keys(); e.hasMoreElements(); ) {
		link_cost lc = (link_cost)topo_db[i].linkCost.get(e.nextElement());
		logMsg = String.format("%s -> R%d link %d cost %d", routerName, i, lc.link, lc.cost);
		writeToLog(logMsg);
	    }
	}
    }

    /****************************************************
     ** Here we will have to apply Dijkstra'salgorithm **
     ****************************************************/ 
    private void updateRIB() throws Exception {
	RIB_node RIB[] = new RIB_node[NBR_ROUTER + 1];
	Vector<Integer> N = new Vector<Integer>();
	// initialization
	N.add(routerId);
	RIB[routerId] = new RIB_node();
	RIB[routerId].curr = 0;
	RIB[routerId].pred = routerId;
	for( int i = 1; i < NBR_ROUTER + 1; i++ ) {
	    if ( !N.contains(i) ) {
		RIB[i] = new RIB_node();
		if ( nb[routerId][i].isNeighbour ) {
		    RIB[i].curr = nb[routerId][i].nLink.cost;
		    RIB[i].pred = i;
		} else {
		    RIB[i].curr = Integer.MAX_VALUE;
		    RIB[i].pred = -1;
		}
	    }
	}

	// algorithm
	for( int i = 0; i < NBR_ROUTER - 1; i++ ) {
	    int w = 0;
	    int min_cost = Integer.MAX_VALUE;
	    // find the minimum D(w) s.t. w is not in N
	    for( int j = 1; j < NBR_ROUTER + 1; j++ ) {
		if ( !N.contains(j) && RIB[j].curr < min_cost ) {
		    w = j;
		    min_cost = RIB[j].curr;
		}
	    }
	    N.add(w);
	    // update D(v) where v are neighbour to w not in N
	    for( int j = 1; j < NBR_ROUTER + 1; j++ ) {
		if ( !N.contains(j) && nb[w][j] != null && nb[w][j].isNeighbour ) {
		    if( RIB[w].curr < Integer.MAX_VALUE && RIB[j].curr > RIB[w].curr + nb[w][j].nLink.cost) {
			RIB[j].curr = RIB[w].curr + nb[w][j].nLink.cost;
			RIB[j].pred = w;
		    }
		}
	    }
	}
	// log RIB
	writeToLog("# RIB");
	for( int i = 1; i < NBR_ROUTER + 1; i++ ) {
	    String p, c;
	    if ( RIB[i].pred == routerId ) {
		p = "Local";
	    } else if( RIB[i].pred > 0 ) {
		p = "R" + Integer.toString(RIB[i].pred);
	    } else {
		p = "INFINITY";
	    }
	    
	    if( RIB[i].curr != Integer.MAX_VALUE ) {
		c = Integer.toString(RIB[i].curr);
	    } else {
		c = "INFINITY";
	    }
	    
	    logMsg = String.format("%s -> R%d -> %s, %s", routerName, i, p, c);
	    writeToLog(logMsg);
	}
    }

    // write line into log file line by line
    private void writeToLog(String s) throws Exception {
	BufferedWriter out = new BufferedWriter(new FileWriter(log, true));
	out.write(s);
	out.newLine();
	out.close();
    }

    /*********************************
     ** Thread used for concurrency **
     *********************************/ 

    /* thread used for receiving HELLO packet from other routers
       terminates when all expected HELLO packet recevied */
    private class ReceiveHELLO implements Runnable {
	public void run() {
	    byte[] rData = null;
	    packet HELLO = null;
	    // initialize neighbour array
	    for( int i = 1; i < NBR_ROUTER + 1; i++ ) {
		for ( int j = 1; j < NBR_ROUTER + 1; j++ ) nb[i][j] = new neighbour();
	    }

	    // receive all HELLO packets and determine neighbors
	    for( int i = 0; i < nbrLink; i++ ) {
		try {
		    rData = receivePacket();
		    HELLO = packet.parseHELLO(rData);
		} catch (Exception e) {}
		int lId = HELLO.getLinkId();
		link_cost lc = (link_cost)topo_db[routerId].linkCost.get(lId);
		nb[routerId][HELLO.getRouterId()].isNeighbour = true;
		nb[routerId][HELLO.getRouterId()].nLink = new link_cost(lId, lc.cost);
	    }
	}
    }

    /* thread used for receiving LS PDU */
    private class ReceiveLSPDU implements Runnable {
	public void run() {
	    byte[] rData = null;
	    packet LSPDU = null; 
	    while(true) {
		// when a LS PDU is received, update topology database
		try {
		    rData = receivePacket();
		    LSPDU = packet.parseLSPDU(rData);
		    // write LS PDU sent to log file
		    logMsg = String.format("%s receives from R%d via link id %d that R%d has a link with id %d and cost %d"
					   , routerName, LSPDU.getSender(), LSPDU.getVia(), LSPDU.getRouterId()
					   , LSPDU.getLinkId(), LSPDU.getCost());
		    writeToLog(logMsg);
		    updateTopologyDB(LSPDU);
		    Thread.sleep(5); // wait for Topology to be updated and write to log
		} catch (Exception e) {}
	    }
	}
    }

    /********************
     ** useful classes **
     ********************/ 

    // neighbour class
    class neighbour {
	boolean isNeighbour = false;
	link_cost nLink = new link_cost(0, 0);
    }

    // link_cost class for Topology Database
    class link_cost {
	int link = 0;
	int cost = 0;
	public link_cost(int l, int c) {
	    link = l;
	    cost = c;
	}
    }

    // RIB class for determining RIB
    class RIB_node {
	int curr;
	int pred;
    }

    // Topology Database
    class topology_db {
	Hashtable linkCost;
    }

    // main
    public static void main(String argv[]) throws Exception {
	router r = new router(argv);
    }
}