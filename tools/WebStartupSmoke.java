import dev.wallpapermenu.wallpaper.ChromiumWebDecoder;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;

public final class WebStartupSmoke {
    public static void main(String[] args) throws Exception {
        long start = System.nanoTime();
        try (var decoder = new ChromiumWebDecoder(Path.of(args[0]), 60)) {
            System.out.printf("Browser initialized in %.3fs%n", (System.nanoTime()-start)/1e9);
            long deadline = start + TimeUnit.SECONDS.toNanos(65);
            while (System.nanoTime() < deadline) {
                if (decoder.failure() != null) throw new AssertionError(decoder.failure());
                byte[] frame = decoder.pollFrame();
                if (frame != null) {
                    int bright = 0;
                    for (int i=0; i<frame.length; i+=4)
                        if ((frame[i]&255)+(frame[i+1]&255)+(frame[i+2]&255)>150) bright++;
                    if (bright > ChromiumWebDecoder.WIDTH*ChromiumWebDecoder.HEIGHT/2) {
                        double seconds = (System.nanoTime()-start)/1e9;
                        System.out.printf("Visible page in %.3fs%n", seconds);
                        long until = System.nanoTime()+TimeUnit.SECONDS.toNanos(5);
                        while(System.nanoTime()<until) {
                            decoder.dispatchMouse("mouseMoved",100,100,-1,0);
                            decoder.dispatchMouse("mousePressed",100,100,0,1);
                            decoder.dispatchMouse("mouseReleased",100,100,0,1);
                            if(decoder.failure()!=null) throw new AssertionError(decoder.failure());
                            Thread.sleep(10);
                        }
                        if(args.length>1 && seconds>Double.parseDouble(args[1])) throw new AssertionError("Startup exceeded deadline");
                        System.out.println("PASS: visible page and concurrent input/frame ACK");
                        return;
                    }
                }
                Thread.sleep(2);
            }
            throw new AssertionError("No visible page in 65 seconds");
        }
    }
}
