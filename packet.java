// packet class modified from CS456 2011 Spring Assignment 2
// common packet class used by both SENDER and RECEIVER

import java.nio.ByteBuffer;
import java.nio.ByteOrder;

public class packet {

    // data members, -1 if variable not available
    private int type;		// 1-INIT, 2-HELLO, 5-LSPDU
    private int sender;
    private int routerId;
    private int linkId;
    private int cost;
    private int via;
	
    //////////////////////// CONSTRUCTORS //////////////////////////////////////////
	
    // hidden constructor to prevent creation of invalid packets
    private packet(int t, int s, int r, int l, int c, int v) {
	type = t;
	sender = s;
	routerId = r;
	linkId = l;
	cost = c;
	via = v;
    }
	
    // special packet constructors to be used in place of hidden constructor
    public static packet createINIT(int r) throws Exception {
	return new packet(1, -1, r, -1, -1, -1);
    }
	
    public static packet createHELLO(int r, int l) throws Exception {
	return new packet(2, -1, r, l, -1, -1);
    }
	
    public static packet createLSPDU(int s, int r, int l, int c, int v) throws Exception {
	return new packet(5, s, r, l, c, v);
    }
	
    ///////////////////////// PACKET DATA //////////////////////////////////////////
	
    public String getType() {
	String s = null;
	switch (type) {
	case 1:
	    s = "INIT";
	    break;
	case 2:
	    s = "HELLO";
	    break;
	case 5:
	    s = "LS PDU";
	    break;
	}
	return s;
    }
	
    public int getSender() {
	return sender;
    }
	
    public int getRouterId() {
	return routerId;
    }
	
    public int getLinkId() {
	return linkId;
    }

    public int getCost() {
	return cost;
    }

    public int getVia() {
	return via;
    }
	
    //////////////////////////// UDP HELPERS ///////////////////////////////////////
	
    public byte[] getUDPdata() {
	ByteBuffer buffer = ByteBuffer.allocate(type*4);
	buffer.order(ByteOrder.LITTLE_ENDIAN);
	switch (type) {
	case 1:
	    buffer.putInt(routerId);
	    break;
	case 2:
	    buffer.putInt(routerId);
	    buffer.putInt(linkId);
	    break;
	case 5:
	    buffer.putInt(sender);
	    buffer.putInt(routerId);
	    buffer.putInt(linkId);
	    buffer.putInt(cost);
	    buffer.putInt(via);
	    break;
	}
	return buffer.array();
    }
	
    public static packet parseLSPDU(byte[] UDPdata) throws Exception {
	ByteBuffer buffer = ByteBuffer.wrap(UDPdata);
	buffer.order(ByteOrder.LITTLE_ENDIAN);
	int s = buffer.getInt();
	int r = buffer.getInt();
	int l = buffer.getInt();
	int c = buffer.getInt();
	int v = buffer.getInt();
	return packet.createLSPDU(s, r, l, c, v);
    }

    public static packet parseHELLO(byte[] data) throws Exception {
	ByteBuffer buffer = ByteBuffer.wrap(data);
	buffer.order(ByteOrder.LITTLE_ENDIAN);
	int r = buffer.getInt();
	int l = buffer.getInt();
	return packet.createHELLO(r, l);
    }
}