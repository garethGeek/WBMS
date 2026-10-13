package wbms.tanroads.wbms.capture;

import com.fazecast.jSerialComm.SerialPort;
import wbms.tanroads.wbms.enums.FlowControlMode;
import wbms.tanroads.wbms.enums.ParityMode;
import wbms.tanroads.wbms.enums.StopBitsMode;

public final class SerialPortConfiguration {

    private SerialPortConfiguration() {
    }

    public static void apply(SerialPort port, Integer baudRate, Integer dataBits,
                             ParityMode parity, StopBitsMode stopBits, FlowControlMode flowControl) {
        port.setBaudRate(baudRate != null ? baudRate : 9600);
        port.setNumDataBits(dataBits != null ? dataBits : 8);
        port.setParity(parityBits(parity));
        port.setNumStopBits(stopBitsBits(stopBits));
        port.setFlowControl(flowControlBits(flowControl));
    }

    private static int parityBits(ParityMode parity) {
        if (parity == null) return SerialPort.NO_PARITY;
        return switch (parity) {
            case ODD -> SerialPort.ODD_PARITY;
            case EVEN -> SerialPort.EVEN_PARITY;
            case MARK -> SerialPort.MARK_PARITY;
            case SPACE -> SerialPort.SPACE_PARITY;
            case NONE -> SerialPort.NO_PARITY;
        };
    }

    private static int stopBitsBits(StopBitsMode stopBits) {
        if (stopBits == StopBitsMode.ONE_POINT_FIVE) return SerialPort.ONE_POINT_FIVE_STOP_BITS;
        if (stopBits == StopBitsMode.TWO) return SerialPort.TWO_STOP_BITS;
        return SerialPort.ONE_STOP_BIT;
    }

    private static int flowControlBits(FlowControlMode flowControl) {
        if (flowControl == FlowControlMode.RTS_CTS) {
            return SerialPort.FLOW_CONTROL_RTS_ENABLED | SerialPort.FLOW_CONTROL_CTS_ENABLED;
        }
        if (flowControl == FlowControlMode.XON_XOFF) {
            return SerialPort.FLOW_CONTROL_XONXOFF_IN_ENABLED | SerialPort.FLOW_CONTROL_XONXOFF_OUT_ENABLED;
        }
        return SerialPort.FLOW_CONTROL_DISABLED;
    }
}
