package wbms.tanroads.wbms.capture;

import com.fazecast.jSerialComm.SerialPort;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.List;

@Service
public class SerialPortService {

    public List<String> listComPorts() {
        return Arrays.stream(SerialPort.getCommPorts())
                .map(SerialPort::getSystemPortName) // COM5
                .sorted()
                .toList();
    }
}