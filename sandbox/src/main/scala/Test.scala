import java.util.zip.{CRC32, Deflater, Inflater}

object Test {
  def main(args: Array[String]): Unit = {
    val input = ("scala-native zlib check " * 64).getBytes("UTF-8")
    val crc = new CRC32()
    crc.update(input)

    val deflater = new Deflater()
    deflater.setInput(input)
    deflater.finish()
    val compressed = new Array[Byte](input.length)
    val clen = deflater.deflate(compressed)
    deflater.end()

    val inflater = new Inflater()
    inflater.setInput(compressed, 0, clen)
    val out = new Array[Byte](input.length)
    val olen = inflater.inflate(out)
    inflater.end()

    assert(olen == input.length && java.util.Arrays.equals(out, input))
    println(s"zlib OK: crc=${crc.getValue} ${input.length} -> $clen bytes")
  }
}
