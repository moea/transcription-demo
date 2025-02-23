package assistant;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.util.Queue;
import java.util.function.Consumer;
import java.util.function.Function;

import javax.sound.sampled.AudioFileFormat;
import javax.sound.sampled.AudioFormat;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.DataLine;
import javax.sound.sampled.LineUnavailableException;
import javax.sound.sampled.TargetDataLine;

public class AudioUtil {
    static AudioFormat AUDIO_FORMAT = new AudioFormat(24000f, 16, 1, true, false);

    public static class Recorder {
        boolean running = false;
        final int bufcount;

        public Recorder(int bufs) {
            bufcount = bufs;
        }

        public Recorder() {
            this(1024);
        }

        public void stop() {
            running = false;
        }

        public void start(Consumer<Object> enqueue) throws Exception {
            TargetDataLine mic;
            DataLine.Info info = new DataLine.Info(
                TargetDataLine.class, AUDIO_FORMAT);
            if (!AudioSystem.isLineSupported(info)) {
                throw new RuntimeException("Line not supported");
            }
            mic = (TargetDataLine) AudioSystem.getLine(info);
            mic.open(AUDIO_FORMAT);

            byte[][] bufs = new byte[bufcount][mic.getBufferSize() / 5];
            mic.start();

            running = true;
            int off = 0;
            while(running) {
                byte[] buf = bufs[off++ % bufcount];
                mic.read(buf, 0, buf.length);
                enqueue.accept(buf);
            }
            mic.stop();
        }
    }
}