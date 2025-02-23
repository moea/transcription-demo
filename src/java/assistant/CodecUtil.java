package assistant;

import java.util.Base64;
import java.util.Base64.Encoder;

public class CodecUtil {
    static final Encoder ENCODER = Base64.getEncoder();

    public static String base64(byte[] bytes) {
        return ENCODER.encodeToString(bytes);
    }
}
