import org.snmp4j.CommunityTarget;
import org.snmp4j.PDU;
import org.snmp4j.event.ResponseEvent;
import org.snmp4j.mp.SnmpConstants;
import org.snmp4j.smi.OID;
import org.snmp4j.smi.OctetString;
import org.snmp4j.smi.UdpAddress;
import org.snmp4j.smi.VariableBinding;

import javax.swing.*;
import java.awt.*;
import java.io.IOException;

public class IOLineWidget extends JPanel {
    private JLabel lineName;
    private JLabel messageText;
    private JPanel rootPanel;

    //<line settings>=================
    private OID trapReceiveOID;
    private OID snmpGetOID;
    private VariableBinding snmpGetVariable;
    private DisplayMessageSettings value1Message;
    private DisplayMessageSettings value0Message;
    //</line settings>================

    private Color defaultBackgroundColor;
    private AutoChecking autoChecking;
    private NetPingWidget netPingWidget;

    IOLineWidget(NetPingWidget netPingWidgetIn){
        netPingWidget = netPingWidgetIn;
        snmpGetOID = null;
        trapReceiveOID = null;
        snmpGetVariable = new VariableBinding(snmpGetOID);
        value1Message = new DisplayMessageSettings();
        value0Message = new DisplayMessageSettings();

        defaultBackgroundColor = this.getBackground();

        lineName.setText("имя линии");
        messageText.setText("неизвестно");

        autoChecking = new AutoChecking(() -> {
            if(trapReceiveOID == null || snmpGetOID == null){
                return;
            }

            PDU pdu = new PDU();
            pdu.add(snmpGetVariable);
            pdu.setType(PDU.GET);

            // Create Target Address object
            CommunityTarget comtarget = new CommunityTarget();
            comtarget.setCommunity(new OctetString(netPingWidget.getSnmpCommunity()));
            comtarget.setVersion(SnmpConstants.version1);
            comtarget.setAddress(new UdpAddress(netPingWidget.getIpAddress() + "/" + netPingWidget.getDeviceName()));
            comtarget.setRetries(netPingWidget.getMainWindow().getSettingsLoader().getSnmpGetRetries());
            comtarget.setTimeout(netPingWidget.getMainWindow().getSettingsLoader().getSnmpGetTimeout());

            //checking.setText("проверка...");

            try {
                ResponseEvent response = netPingWidget.getMainWindow().getSnmp().get(pdu, comtarget);

                // Process Agent Response
                if (response != null) {
                    PDU responsePDU = response.getResponse();

                    if (responsePDU != null) {
                        int errorStatus = responsePDU.getErrorStatus();
                        int errorIndex = responsePDU.getErrorIndex();
                        String errorStatusText = responsePDU.getErrorStatusText();

                        if (errorStatus == PDU.noError) {
                            if (responsePDU.getVariable(snmpGetOID) != null) {
                                int value = responsePDU.getVariable(snmpGetOID).toInt();

                                if (value == 1) {
                                    set1();
                                } else if(value == 0){
                                    set0();
                                }
                            }
                        } else {
                            System.out.println("Error: Request Failed");
                            System.out.println("Error Status = " + errorStatus);
                            System.out.println("Error Index = " + errorIndex);
                            System.out.println("Error Status Text = " + errorStatusText);
                            setError();
                        }
                    } else {
                        //System.out.println("Error: Response PDU is null");
                        setError();
                    }
                } else {
                    //System.out.println("Error: Agent Timeout... ");
                    setError();
                }

                //checking.setText("");
            } catch (IOException ex) {
                ex.printStackTrace();
            }
        }, netPingWidget.getMainWindow().getSettingsLoader().getCheckingDelay());
    }

    public void setLineName(String lineNameIn){
        lineName.setText(lineNameIn);
    }
    public void setSnmpGetOID(String snmpGetOIDIn){
        snmpGetOID = new OID(snmpGetOIDIn);
    }
    public void setTrapReceiveOID(String trapReceiveOIDIn){
        trapReceiveOID = new OID(trapReceiveOIDIn);
        snmpGetVariable = new VariableBinding(trapReceiveOID);
    }
    public void set1(){
        rootPanel.setBackground(value1Message.backgroundColor);
        messageText.setText(value1Message.messageText);
        messageText.setForeground(value1Message.textColor);
        lineName.setForeground(value1Message.textColor);
    }
    public void set0(){
        rootPanel.setBackground(value0Message.backgroundColor);
        messageText.setText(value0Message.messageText);
        messageText.setForeground(value0Message.textColor);
        lineName.setForeground(value0Message.textColor);
    }
    public void setError(){
        rootPanel.setBackground(defaultBackgroundColor);
        messageText.setText("Ошибка");
        messageText.setForeground(Color.BLACK);
        lineName.setForeground(Color.BLACK);
    }

    public String getLineName(){
        return lineName.getText();
    }
    public DisplayMessageSettings getValue0Message(){
        return value0Message;
    }
    public DisplayMessageSettings getValue1Message(){
        return value1Message;
    }
    public OID getTrapReceiveOID(){
        return trapReceiveOID;
    }
    public OID getSnmpGetOID(){
        return snmpGetOID;
    }
    public AutoChecking getAutoChecking(){
        return autoChecking;
    }
}