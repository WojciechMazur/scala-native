import java.nio.file.*;
import java.nio.file.attribute.*;

public class FileKeyProbe {
  public static void main(String[] args) throws Exception {
    Path dir = Files.createTempDirectory("fk");
    Path f = Files.createFile(dir.resolve("f"));
    Path l = Files.createLink(dir.resolve("l"), f);
    for (Path p : new Path[] {dir, f, l}) {
      Object basic = Files.readAttributes(p, BasicFileAttributes.class).fileKey();
      Object dos = Files.readAttributes(p, DosFileAttributes.class).fileKey();
      Object attr = Files.getAttribute(p, "fileKey");
      System.out.println("JVM-PROBE " + p.getFileName() + " basic=" + basic + " dos=" + dos + " attr=" + attr);
    }
  }
}
