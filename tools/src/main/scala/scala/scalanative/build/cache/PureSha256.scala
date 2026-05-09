package scala.scalanative
package build
package cache

/** Minimal SHA-256 (RFC 6234) without `java.security`, for Scala Native–linked code. */
private[scalanative] object PureSha256 {

  def apply(data: Array[Byte]): Array[Byte] = {
    val bitLen = data.length.toLong * 8L
    val paddedLen = ((data.length + 9 + 63) / 64) * 64
    val buf = new Array[Byte](paddedLen)
    System.arraycopy(data, 0, buf, 0, data.length)
    buf(data.length) = 0x80.toByte
    val i = paddedLen - 8
    var bi = 0
    while (bi < 4) {
      buf(i + bi) = 0.toByte
      bi += 1
    }
    val loBits = bitLen.toInt
    while (bi < 8) {
      buf(i + bi) = ((loBits >>> (24 - (bi - 4) * 8)) & 0xff).toByte
      bi += 1
    }

    var h0 = 0x6a09e667
    var h1 = 0xbb67ae85
    var h2 = 0x3c6ef372
    var h3 = 0xa54ff53a
    var h4 = 0x510e527f
    var h5 = 0x9b05688c
    var h6 = 0x1f83d9ab
    var h7 = 0x5be0cd19

    val kRound = Sha256Constants.k
    var offset = 0
    while (offset < paddedLen) {
      val w = new Array[Int](64)
      var t = 0
      while (t < 16) {
        val j = offset + t * 4
        w(t) =
          (buf(j) & 0xff) << 24 |
            (buf(j + 1) & 0xff) << 16 |
            (buf(j + 2) & 0xff) << 8 |
            (buf(j + 3) & 0xff)
        t += 1
      }
      while (t < 64) {
        val s0 = smallSigma0(w(t - 15))
        val s1 = smallSigma1(w(t - 2))
        w(t) = w(t - 16) + s0 + w(t - 7) + s1
        t += 1
      }

      var a = h0
      var b = h1
      var c = h2
      var d = h3
      var e = h4
      var f = h5
      var g = h6
      var h = h7
      t = 0
      while (t < 64) {
        val t1 = h + bigSigma1(e) + ch(e, f, g) + kRound(t) + w(t)
        val t2 = bigSigma0(a) + maj(a, b, c)
        h = g
        g = f
        f = e
        e = d + t1
        d = c
        c = b
        b = a
        a = t1 + t2
        t += 1
      }
      h0 += a
      h1 += b
      h2 += c
      h3 += d
      h4 += e
      h5 += f
      h6 += g
      h7 += h
      offset += 64
    }

    val out = new Array[Byte](32)
    def putInt(idx: Int, v: Int): Unit = {
      out(idx) = ((v >>> 24) & 0xff).toByte
      out(idx + 1) = ((v >>> 16) & 0xff).toByte
      out(idx + 2) = ((v >>> 8) & 0xff).toByte
      out(idx + 3) = (v & 0xff).toByte
    }
    putInt(0, h0)
    putInt(4, h1)
    putInt(8, h2)
    putInt(12, h3)
    putInt(16, h4)
    putInt(20, h5)
    putInt(24, h6)
    putInt(28, h7)
    out
  }

  @inline private def rotr(x: Int, n: Int): Int =
    (x >>> n) | (x << (32 - n))

  @inline private def ch(x: Int, y: Int, z: Int): Int =
    (x & y) ^ (~x & z)

  @inline private def maj(x: Int, y: Int, z: Int): Int =
    (x & y) ^ (x & z) ^ (y & z)

  @inline private def bigSigma0(x: Int): Int =
    rotr(x, 2) ^ rotr(x, 13) ^ rotr(x, 22)

  @inline private def bigSigma1(x: Int): Int =
    rotr(x, 6) ^ rotr(x, 11) ^ rotr(x, 25)

  @inline private def smallSigma0(x: Int): Int =
    rotr(x, 7) ^ rotr(x, 18) ^ (x >>> 3)

  @inline private def smallSigma1(x: Int): Int =
    rotr(x, 17) ^ rotr(x, 19) ^ (x >>> 10)

  private object Sha256Constants {
    val k: Array[Int] = Array(
      0x428a2f98, 0x71374491, 0xb5c0fbcf, 0xe9b5dba5, 0x3956c25b, 0x59f111f1, 0x923f82a4, 0xab1c5ed5,
      0xd807aa98, 0x12835b01, 0x243185be, 0x550c7dc3, 0x72be5d74, 0x80deb1fe, 0x9bdc06a7, 0xc19bf174,
      0xe49b69c1, 0xefbe4786, 0x0fc19dc6, 0x240ca1cc, 0x2de92c6f, 0x4a7484aa, 0x5cb0a9dc, 0x76f988da,
      0x983e5152, 0xa831c66d, 0xb00327c8, 0xbf597fc7, 0xc6e00bf3, 0xd5a79147, 0x06ca6351, 0x14292967,
      0x27b70a85, 0x2e1b2138, 0x4d2c6dfc, 0x53380d13, 0x650a7354, 0x766a0abb, 0x81c2c92e, 0x92722c85,
      0xa2bfe8a1, 0xa81a664b, 0xc24b8b70, 0xc76c51a3, 0xd192e819, 0xd6990624, 0xf40e3585, 0x106aa070,
      0x19a4c116, 0x1e376c08, 0x2748774c, 0x34b0bcb5, 0x391c0cb3, 0x4ed8aa4a, 0x5b9cca4f, 0x682e6ff3,
      0x748f82ee, 0x78a5636f, 0x84c87814, 0x8cc70208, 0x90befffa, 0xa4506ceb, 0xbef9a3f7, 0xc67178f2
    )
  }
}
