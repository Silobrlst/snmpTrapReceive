import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.charset.Charset;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.snmp4j.CommandResponder;
import org.snmp4j.CommandResponderEvent;
import org.snmp4j.CommunityTarget;
import org.snmp4j.MessageDispatcher;
import org.snmp4j.MessageDispatcherImpl;
import org.snmp4j.MessageException;
import org.snmp4j.PDU;
import org.snmp4j.Snmp;
import org.snmp4j.log.LogFactory;
import org.snmp4j.mp.MPv1;
import org.snmp4j.mp.MPv2c;
import org.snmp4j.mp.StateReference;
import org.snmp4j.mp.StatusInformation;
import org.snmp4j.security.Priv3DES;
import org.snmp4j.security.SecurityProtocols;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.TcpAddress;
import org.snmp4j.smi.TransportIpAddress;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.tools.console.SnmpRequest;
import org.snmp4j.transport.AbstractTransportMapping;
import org.snmp4j.transport.DefaultTcpTransportMapping;
import org.snmp4j.transport.DefaultUdpTransportMapping;
import org.snmp4j.util.MultiThreadedMessageDispatcher;
import org.snmp4j.util.ThreadPool;

import javax.swing.*;

public class Main implements CommandResponder {
    static Map<String, NetpingWidget> map = new HashMap<>();

    static JPanel gridPanel;
    static TrayIcon trayIcon;

    public Main() {
        createGUI();
    }

    private static String loadDataFromfile(String fileNameIn){
        try{
            File file = new File(fileNameIn);
            file.createNewFile();

            byte[] encoded = Files.readAllBytes(Paths.get(fileNameIn));
            return new String(encoded, Charset.defaultCharset());
        }catch(IOException e){
            e.printStackTrace();
        }

        return null;
    }

    private static JSONObject loadJSON(String fileNameIn){
        String data = loadDataFromfile(fileNameIn);

        if(data.isEmpty()){
            JSONObject json = new JSONObject();
            json.put("ip-address", "0.0.0.0");
            json.put("port", "162");

            saveDataToFile(fileNameIn, json.toString());

            return json;
        }

        JSONObject json = new JSONObject(data);

        if(!json.has("ip-address")){
            json.put("ip-address", "0.0.0.0");
        }

        if(!json.has("port")){
            json.put("port", "162");
        }

        return json;
    }

    private static void saveDataToFile(String fileNameIn, String dataIn){
        BufferedWriter bw = null;
        FileWriter fw = null;

        try {
            fw = new FileWriter(fileNameIn);
            bw = new BufferedWriter(fw);
            bw.write(dataIn);

        } catch (IOException e) {
            e.printStackTrace();
        } finally {
            try {
                if (bw != null)
                    bw.close();

                if (fw != null)
                    fw.close();

            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }
    }

    public static void main(String[] args) {
        setTrayIcon();

        JSONObject json = loadJSON("config.json");

        String address = json.getString("ip-address") + "/" + json.getString("port");

        Main snmp4jTrapReceiver = new Main();
        try {
            snmp4jTrapReceiver.listen(new UdpAddress(address));
        } catch (IOException e) {
            System.err.println("Error in Listening for Trap");
            System.err.println("Exception Message = " + e.getMessage());
            JOptionPane.showMessageDialog(null, e.getMessage(), "Error in Listening for Trap", JOptionPane.ERROR_MESSAGE);
        }
    }

    /**
     * This method will listen for traps and response pdu's from SNMP agent.
     */
    public synchronized void listen(TransportIpAddress address) throws IOException {
        AbstractTransportMapping transport;
        if (address instanceof TcpAddress) {
            transport = new DefaultTcpTransportMapping((TcpAddress) address);
        } else {
            transport = new DefaultUdpTransportMapping((UdpAddress) address);
        }

        ThreadPool threadPool = ThreadPool.create("DispatcherPool", 10);
        MessageDispatcher mtDispatcher = new MultiThreadedMessageDispatcher(threadPool, new MessageDispatcherImpl());

        // add message processing models
        mtDispatcher.addMessageProcessingModel(new MPv1());
        mtDispatcher.addMessageProcessingModel(new MPv2c());

        // add all security protocols
        SecurityProtocols.getInstance().addDefaultProtocols();
        SecurityProtocols.getInstance().addPrivacyProtocol(new Priv3DES());

        //Create Target
        CommunityTarget target = new CommunityTarget();
        target.setCommunity(new OctetString("public"));

        Snmp snmp = new Snmp(mtDispatcher, transport);
        snmp.addCommandResponder(this);

        transport.listen();
        System.out.println("Listening on " + address);
        String message = "trap receiver executed!\nListening on " + address;
        trayIcon.displayMessage("trap receiver", message, TrayIcon.MessageType.INFO);

        try {
            this.wait();
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }

    /**
     * This method will be called whenever a pdu is received on the given port specified in the listen() method
     */
    public synchronized void processPdu(CommandResponderEvent cmdRespEvent) {
        System.out.println("Received PDU...");
        PDU pdu = cmdRespEvent.getPDU();
        if (pdu != null) {

            System.out.println("Trap Type = " + pdu.getType());
            System.out.println("Variable Bindings = " + pdu.getVariableBindings());

            JOptionPane.showMessageDialog(null, pdu.toString(), "Trap received!", JOptionPane.INFORMATION_MESSAGE);

            int pduType = pdu.getType();
            if ((pduType != PDU.TRAP) && (pduType != PDU.V1TRAP) && (pduType != PDU.REPORT)
                    && (pduType != PDU.RESPONSE)) {
                pdu.setErrorIndex(0);
                pdu.setErrorStatus(0);
                pdu.setType(PDU.RESPONSE);
                StatusInformation statusInformation = new StatusInformation();
                StateReference ref = cmdRespEvent.getStateReference();
                try {
                    System.out.println(cmdRespEvent.getPDU());
                    cmdRespEvent.getMessageDispatcher().returnResponsePdu(cmdRespEvent.getMessageProcessingModel(),
                            cmdRespEvent.getSecurityModel(), cmdRespEvent.getSecurityName(), cmdRespEvent.getSecurityLevel(),
                            pdu, cmdRespEvent.getMaxSizeResponsePDU(), ref, statusInformation);
                } catch (MessageException ex) {
                    System.err.println("Error while sending response: " + ex.getMessage());
                    LogFactory.getLogger(SnmpRequest.class).error(ex);
                }
            }
        }
    }


    private static void setTrayIcon() {
        PopupMenu trayMenu = new PopupMenu();
        MenuItem item = new MenuItem("Exit");
        item.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                System.exit(0);
            }
        });
        trayMenu.add(item);

        Image icon = Toolkit.getDefaultToolkit().getImage("icon.png");
        trayIcon = new TrayIcon(icon, "trap receiver", trayMenu);
        trayIcon.setImageAutoSize(true);

        SystemTray tray = SystemTray.getSystemTray();
        try {
            tray.add(trayIcon);
        } catch (AWTException e) {
            e.printStackTrace();
        }
    }


    private static void addNetping(String nameIn, String ipAddressIn){
        NetpingWidget netping = new NetpingWidget(nameIn, ipAddressIn);
        map.put(nameIn, netping);

        netping.setOpened(false);

        gridPanel.add(netping);
        gridPanel.revalidate();
        gridPanel.repaint();
    }

    private static void setNetpingOpened(String netpingNameIn, boolean openedIn){
        map.get(netpingNameIn).setOpened(openedIn);
    }

    private static void setNetpingDisconnected(String netpingNameIn){
        map.get(netpingNameIn).setDisconnected();
    }


    private static void createGUI()
    {
        JFrame frame = new JFrame("Netping мониторинг");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel rootPanel = new JPanel();
        rootPanel.setLayout(new BoxLayout(rootPanel, BoxLayout.Y_AXIS));

        JPanel toolPanel = new JPanel();
        JButton addButton = new JButton("добавить");
        JButton deleteButton = new JButton("удалить");
        toolPanel.add(addButton);
        toolPanel.add(deleteButton);

        addButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                new AddNetpingWindow(new AddNetpingInterface() {
                    @Override
                    public void add(String deviceNameIn, String ipAddressIn) {
                        addNetping(deviceNameIn, ipAddressIn);
                    }
                });
            }
        });

        gridPanel = new JPanel();
        gridPanel.setLayout(new GridLayout(5, 5));

        addNetping("ХАДТ эстакада", "192.168.1.214");
        addNetping("пристанционный узел", "192.168.1.207");

        rootPanel.add(toolPanel);
        rootPanel.add(gridPanel);

        frame.getContentPane().add(rootPanel);
        frame.pack();
        frame.setVisible(true);
    }
}