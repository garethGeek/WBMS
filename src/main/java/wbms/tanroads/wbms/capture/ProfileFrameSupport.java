package wbms.tanroads.wbms.capture;

import wbms.tanroads.wbms.entities.WeighbridgeDevice;

import java.nio.charset.StandardCharsets;

public final class ProfileFrameSupport {
    private ProfileFrameSupport() { }

    public static boolean enabled(WeighbridgeDevice device) {
        return device.getProfileStartBytes() != null && !device.getProfileStartBytes().isBlank()
                && device.getProfileEndBytes() != null && !device.getProfileEndBytes().isBlank();
    }

    public static boolean assigned(WeighbridgeDevice device) {
        return device.getIndicatorProfileUuid() != null && !device.getIndicatorProfileUuid().isBlank();
    }

    public static boolean containsControlFraming(String value) {
        return value != null && (value.indexOf('\u0001') >= 0 || value.indexOf('\u0002') >= 0 || value.indexOf('\u0003') >= 0);
    }

    public static String payload(WeighbridgeDevice device, byte[] frame) {
        int start = CustomFrameDecoder.parseHex(device.getProfileStartBytes()).length;
        int end = CustomFrameDecoder.parseHex(device.getProfileEndBytes()).length;
        int from = Math.min(start, frame.length);
        int to = Math.max(from, frame.length - end);
        String value = new String(frame, from, to - from, StandardCharsets.US_ASCII);
        if (device.getProfileRemoveBeginning() != null && device.getProfileRemoveBeginning() > 0 && value.length() > device.getProfileRemoveBeginning()) value = value.substring(device.getProfileRemoveBeginning());
        if (device.getProfileRemoveEnd() != null && device.getProfileRemoveEnd() > 0 && value.length() > device.getProfileRemoveEnd()) value = value.substring(0, value.length() - device.getProfileRemoveEnd());
        if (device.getProfileStringLength() != null && device.getProfileStringLength() > 0 && value.length() > device.getProfileStringLength()) value = value.substring(0, device.getProfileStringLength());
        return value.trim();
    }
}
