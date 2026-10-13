package com.tanroads.weighbridge.profilebuilder.transport;

import java.util.ArrayList;
import java.util.List;

import com.fazecast.jSerialComm.SerialPort;

/** Lists the COM ports the operating system currently exposes, for the UI port picker. */
public final class SerialPorts {

    public record PortInfo(String systemPortName, String descriptivePortName, String portDescription) { }

    public record Listing(List<PortInfo> ports, String error) { }

    private SerialPorts() {
    }

    public static Listing list() {
        List<PortInfo> ports = new ArrayList<>();
        try {
            for (SerialPort port : SerialPort.getCommPorts()) {
                ports.add(new PortInfo(port.getSystemPortName(), port.getDescriptivePortName(), port.getPortDescription()));
            }
            return new Listing(ports, null);
        } catch (Throwable t) {
            return new Listing(ports, "Serial port enumeration unavailable on this computer: " + t.getMessage());
        }
    }
}
