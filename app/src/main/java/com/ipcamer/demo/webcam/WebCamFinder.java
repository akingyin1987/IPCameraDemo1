package com.ipcamer.demo.webcam;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Logger;

public class WebCamFinder {

    private Logger logger = Logger.getLogger("");
    public final static short SUCCESS = 0;
    public final static short USER_ERROR = 1;
    public final static short PASSWORD_ERROR = 5;
    public final static short PRI_ERROR = 6;
    public final static short UNKNOWN_ERROR = -1;

    public  final  static   int  PORT=80;

    public WebCamFinder() {

    }

    /**
     * Return a searchable map of the cameras ID's.  Key is the camera ID.
     *
     * @return
     * @throws IOException
     */
    public Map<String, WebCamBean> findMap() throws IOException {
        Map<String, WebCamBean> rtnMap = new HashMap<>();

        List<WebCamBean> list = findList();
        for(WebCamBean webCamBean : list) {
            rtnMap.put(webCamBean.getCameraID(), webCamBean);
        }

        return(rtnMap);
    }

    /**
     * This sends a initialization request to the camera changing the camera's network settings.
     *
     * @param newWebCamBean - contains the new settings
     * @param originalIP - the orginal IP address of the camera
     * @param factoryUserName
     * @param factoryPassword
     * @return SUCCESS, USER_ERROR, PASSWORD_ERROR or PRI_ERROR
     * @throws IOException
     */
    public int sendInitRequest(WebCamBean newWebCamBean, String originalIP, String factoryUserName, String factoryPassword) throws IOException {
        int rtnValue = UNKNOWN_ERROR;
        /*
         * 0000   4d 4f 5f 49 02 00 00 00 00 00 00 00 00 00 00 40  MO_I...........@
         * 0010   00 00 00 00 00 00 00 00 00 00 01 37 38 41 35 44  ...........78A5D
         * 0020   44 30 30 43 37 37 44 00 61 64 6d 69 6e 00 00 00  D00C77D.admin...
         * 0030   00 00 00 00 00 31 32 33 34 35 36 00 00 00 00 00  .....123456.....
         * 0040   00 00 c0 a8 01 a6 ff ff ff 00 c0 a8 01 01 4b 4b  ..............KK
         * 0050   4b 4b 00 50 00 00 00                             KK.P...
         */
        ByteArrayOutputStream outp = new ByteArrayOutputStream();
        outp.write("MO_I".getBytes());
        // Operation Code INT16
        outp.write(new byte[]{0x02, 0x00});
        // Reserve INT8
        outp.write(new byte[]{0x00});
        // Reserve BINARY_STREAM[8]
        for(int i = 0; i < 8; i++) {
            outp.write(new byte[]{0x00});
        }
        // Text length
        outp.write(new byte[]{0x40, 0x00, 0x00, 0x00});
        // UNKNOWN
        outp.write(new byte[]{0x00, 0x00, 0x00, 0x00});
        // Reserved
        outp.write(new byte[]{0x00, 0x00, 0x00, 0x01});

        // Text
        outp.write(WebCamFinderUtils.getFixedField(newWebCamBean.getCameraID(), 13));
        outp.write(WebCamFinderUtils.getFixedField(factoryUserName, 13));
        outp.write(WebCamFinderUtils.getFixedField(factoryPassword, 13));
        outp.write(WebCamFinderUtils.getIPAddressAsBytes(newWebCamBean.getIpAddress()));
        outp.write(WebCamFinderUtils.getIPAddressAsBytes(newWebCamBean.getSubnetMask()));
        outp.write(WebCamFinderUtils.getIPAddressAsBytes(newWebCamBean.getGatewayIP()));
        outp.write(WebCamFinderUtils.getIPAddressAsBytes(newWebCamBean.getDNS()));
        int port = Integer.valueOf(newWebCamBean.getCameraPort()).intValue();
        outp.write(WebCamFinderUtils.convIntToINT16(port));
        // pad out to text message to 64
        outp.write(new byte[]{0x00, 0x00, 0x00});

        String hexArray = WebCamFinderUtils.byteArrayToHex(outp.toByteArray());
        logger.info("Send Request: " + hexArray);

        DatagramSocket socket = null;
        try {
            DatagramPacket sendPacket = new DatagramPacket(outp.toByteArray(), outp.size());

            // Create an address
            InetAddress destAddress = InetAddress.getByName("255.255.255.255");
            sendPacket.setAddress(destAddress);
            sendPacket.setPort(PORT);

            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setReuseAddress(true);
            socket.setSoTimeout(5000);
            socket.send(sendPacket);
            logger.fine("Sent: " + WebCamFinderUtils.byteArrayToHex(sendPacket.getData()));

            byte[] b = new byte[1024];
            DatagramPacket dgram = new DatagramPacket(b, b.length);

            while(true) {
                try {
                    socket.receive(dgram); // blocks until a datagram is received
                } catch (SocketTimeoutException se) {
                    break;
                }
                logger.fine("Received: " + WebCamFinderUtils.byteArrayToHex(dgram.getData()));
                InetAddress sourceIP = dgram.getAddress();
                if(sourceIP.getHostAddress().contains(originalIP)) {
                    // Get the last 2 bytes
                    byte[] rtnValueBytes = Arrays.copyOfRange(dgram.getData(), (dgram.getLength()-2), dgram.getLength());
                    rtnValue = WebCamFinderUtils.convINT16ToShort(rtnValueBytes);
                    logger.fine("rtnValue=" + rtnValue);
                    break;
                }
                dgram.setLength(b.length); // must reset length field!
            }
        }
        catch(Exception e) {
            throw(new IOException("Unable to send init_req packet.", e));
        }
        finally {
            if(socket != null) {
                socket.close();
            }
        }

        return(rtnValue);
    }

    /**
     * This method sends out the search request to all the IP Cameras on the network.
     *
     * @return list of IP Camera's found.
     * @throws IOException
     */
    public List<WebCamBean> findList() throws IOException {
        List<WebCamBean> foundList = new ArrayList<WebCamBean>();

        DatagramSocket socket = null;
        try {
            byte[] sendData = new byte[]{0x4d, 0x4f, 0x5f, 0x49, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x04,
                    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x01};
            /*
             * 0000   4d 4f 5f 49 00 00 00 00 00 00 00 00 00 00 00 04
             * 0010   00 00 00 00 00 00 00 00 00 00 01
             */
            DatagramPacket sendPacket = new DatagramPacket(sendData, sendData.length);

            // Create an address
            InetAddress destAddress = InetAddress.getByName("255.255.255.255");
            sendPacket.setAddress(destAddress);
            sendPacket.setPort(PORT);

            socket = new DatagramSocket();
            socket.setBroadcast(true);
            socket.setReuseAddress(true);
            socket.setSoTimeout(5000);
            socket.send(sendPacket);

            byte[] b = new byte[1024];
            DatagramPacket dgram = new DatagramPacket(b, b.length);

            while(true) {
                try {
                    socket.receive(dgram); // blocks until a datagram is received
                } catch (SocketTimeoutException se) {
                    break;
                }
                logger.fine(WebCamFinderUtils.byteArrayToHex(dgram.getData()));
                foundList.add(getBean(dgram.getData()));
                dgram.setLength(b.length); // must reset length field!
            }
        }
        catch(Exception e) {
            throw(new IOException("Unable to search.", e));
        }
        finally {
            if(socket != null) {
                socket.close();
            }
        }

        return(foundList);
    }

    /**
     * Converts the response from the IPCamera into our WebCamBean.
     *
     * @param respData
     * @return
     */
    private WebCamBean getBean(byte[] respData) {
        WebCamBean rtnBean = new WebCamBean();

        rtnBean.setCameraID(WebCamFinderUtils.getTrimmed(Arrays.copyOfRange(respData,23,35)));
        rtnBean.setCameraName(WebCamFinderUtils.getTrimmed(Arrays.copyOfRange(respData,36,56)));
        rtnBean.setIpAddress(Arrays.copyOfRange(respData,57,61));
        rtnBean.setSubnetMask(Arrays.copyOfRange(respData,61,65));
        rtnBean.setGatewayIP(Arrays.copyOfRange(respData,65,69));
        rtnBean.setDNS(Arrays.copyOfRange(respData,69,73));
        // 4 bytes reserved
        rtnBean.setSysSoftwareVers(Arrays.copyOfRange(respData,77,81));
        rtnBean.setAppSoftwareVers(Arrays.copyOfRange(respData,81,85));
        rtnBean.setCameraPort(Arrays.copyOfRange(respData,85,87));

        return rtnBean;
    }

    public static String getUsage() {
        return("java -jar webcamfinder.jar [-cameraID=<camera ID> -newIP=<IP Address> [-newPort=<port number> -factoryUserName=<user name> " +
                "-factoryPassword=<password> -newGateway=<IP Address> -newDNS=<IP Address> -newNetMask=<Net Mask>]]");
    }

    /**
     * @param args
     */
    public static void main(String[] args) {
        WebCamFinder webCamFinder = new WebCamFinder();
        try {
            if(args.length == 0) {
                List<WebCamBean> foundList = webCamFinder.findList();
                for(WebCamBean webCamBean: foundList) {
                    System.out.println(webCamBean);
                }
                System.out.println("size="+foundList.size());
            }
            else {
                String originalIP = null;
                String cameraID = null;
                String factoryUserName = "admin";
                String factoryPassword = "123456";
                String newIPAddress = null;
                String newNetMask = "255.255.255.0";
                String newDNS = null;
                String newGateway = "192.168.1.1";
                int newPort = 80;

                for(String arg : args) {
                    if(arg.equalsIgnoreCase("-?")) {
                        System.err.println(getUsage());
                        System.exit(-1);
                    }
                    else if(arg.contains("=")) {
                        String[] split = arg.split("=");
                        if("-cameraID".equalsIgnoreCase(split[0])) {
                            cameraID = split[1];
                        }
                        else if("-factoryUserName".equalsIgnoreCase(split[0])) {
                            factoryUserName = split[1];
                        }
                        else if("-factoryPassword".equalsIgnoreCase(split[0])) {
                            factoryPassword = split[1];
                        }
                        else if("-newIP".equalsIgnoreCase(split[0])) {
                            newIPAddress = split[1];
                        }
                        else if("-newGateway".equalsIgnoreCase(split[0])) {
                            newGateway = split[1];
                        }
                        else if("-newNetMask".equalsIgnoreCase(split[0])) {
                            newNetMask = split[1];
                        }
                        else if("-newDNS".equalsIgnoreCase(split[0])) {
                            newDNS = split[1];
                        }
                        else if("-newPort".equalsIgnoreCase(split[0])) {
                            newPort = Integer.parseInt(split[1]);
                        }
                    }
                }
                if(newIPAddress == null || cameraID == null) {
                    System.err.println(getUsage());
                    System.exit(-1);
                }
                Map<String, WebCamBean> foundMap = null;
                int retryCount = 0;
                WebCamBean webCamBean = null;
                do {
                    foundMap = webCamFinder.findMap();
                    webCamBean = foundMap.get(cameraID);
                    retryCount++;
                }
                while(webCamBean == null && retryCount < 3);
                if(webCamBean == null) {
                    System.err.println("Please check the camera ID you passed because it was not found, cameraID=" + cameraID);
                    System.exit(-1);
                }
                else {
                    System.out.println("Camera ID " + cameraID + " found!");
                    System.out.println("Current settings: " + webCamBean);
                    originalIP = webCamBean.getIpAddress();
                    WebCamBean newWebCamBean = (WebCamBean) webCamBean.clone();
                    newWebCamBean.setIpAddress(newIPAddress);
                    newWebCamBean.setGatewayIP(newGateway);
                    newWebCamBean.setDNS(newDNS);
                    newWebCamBean.setSubnetMask(newNetMask);
                    newWebCamBean.setCameraPort("" + newPort);

                    System.out.println("Sending new settings: " + newWebCamBean);
                    int rtnVal = SUCCESS;
                    if((rtnVal = webCamFinder.sendInitRequest(newWebCamBean, originalIP, factoryUserName, factoryPassword)) == SUCCESS) {
                        System.out.println("Send Init_Req was successful.");
                        System.out.println("Sleeping 60 seconds...");
                        try {
                            Thread.sleep((60L * 1000L));
                        }
                        catch(InterruptedException ie) {
                            System.err.println("Thread interrupted!");
                        }
                        retryCount = 0;
                        webCamBean = null;
                        do {
                            foundMap = webCamFinder.findMap();
                            webCamBean = foundMap.get(cameraID);
                            retryCount++;
                        }
                        while(webCamBean == null && retryCount < 3);
                        if(webCamBean == null) {
                            System.err.println("Camera ID " + cameraID + " not found!!");
                            System.exit(-1);
                        }
                        else {
                            System.out.println("New webcam settings: " + webCamBean);
                        }
                    }
                    else {
                        switch(rtnVal) {
                            case USER_ERROR:
                                System.err.println("User error encountered. Please check your settings.");
                                System.exit(-1);
                            case PASSWORD_ERROR:
                                System.err.println("Password error encountered. Please check your settings.");
                                System.exit(-1);
                            case PRI_ERROR:
                                System.err.println("PRI error encountered. Please check your settings.");
                                System.exit(-1);
                            default:
                                System.err.println("Unknown error encountered. Please check your settings.");
                                System.exit(-1);
                        }
                    }
                }
            }
        }
        catch(Exception e) {
            e.printStackTrace(System.err);
        }
    }
}
