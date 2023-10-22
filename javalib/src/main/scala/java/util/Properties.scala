package java.util

import java.io._
import java.{lang => jl}
import java.{util => ju}
import java.nio.charset.StandardCharsets

import scala.annotation.switch
import scala.annotation.tailrec

import java.util.function.{Function, BiConsumer, BiFunction}

class Properties(protected val defaults: Properties)
    extends ju.Hashtable[AnyRef, AnyRef] {

  private var map = new ju.concurrent.ConcurrentHashMap[AnyRef, AnyRef]()

  def this() = this(null)

  def setProperty(key: String, value: String): AnyRef =
    put(key, value)

  def load(inStream: InputStream): Unit = {
    val stream = new BufferedInputStream(inStream)
    loadImpl(new InputStreamReader(stream, StandardCharsets.ISO_8859_1))
  }

  def load(reader: Reader): Unit =
    loadImpl(reader)

  def getProperty(key: String): String =
    getProperty(key, defaultValue = null)

  def getProperty(key: String, defaultValue: String): String = {
    get(key) match {
      case value: String => value

      case _ =>
        if (defaults != null) defaults.getProperty(key, defaultValue)
        else defaultValue
    }
  }

  def propertyNames(): ju.Enumeration[_] = {
    val propNames = new ju.HashSet[String]
    foreachAncestor { ancestor =>
      ancestor.keySet().forEach { key =>
        // Explicitly use asInstanceOf, to trigger the ClassCastException mandated by the spec
        propNames.add(key.asInstanceOf[String])
      }
    }
    Collections.enumeration(propNames)
  }

  def stringPropertyNames(): ju.Set[String] = {
    val set = new ju.HashSet[String]
    foreachAncestor { ancestor =>
      ancestor.entrySet().forEach { entry =>
        (entry.getKey(), entry.getValue()) match {
          case (key: String, _: String) => set.add(key)
          case _                        => // Ignore key
        }
      }
    }
    set
  }

  @inline @tailrec
  private final def foreachAncestor(f: Properties => Unit): Unit = {
    f(this)
    if (defaults ne null)
      defaults.foreachAncestor(f)
  }

  private def format(entry: ju.Map.Entry[AnyRef, AnyRef]): String = {
    def format(s: String): String =
      if (s.length > 40) s"${s.substring(0, 37)}..." else s

    val key: String = entry.getKey().asInstanceOf[String]
    val value: String = entry.getValue().asInstanceOf[String]

    s"${key}=${format(value)}"
  }

  private final val listStr = "-- listing properties --"

  def list(out: PrintStream): Unit = {
    out.println(listStr)
    entrySet().forEach { entry => out.println(format(entry)) }
  }

  def list(out: PrintWriter): Unit = {
    out.println(listStr)
    entrySet().forEach { entry => out.println(format(entry)) }
  }

  def store(out: OutputStream, comments: String): Unit = {
    val writer = new OutputStreamWriter(out, StandardCharsets.ISO_8859_1)
    storeImpl(writer, comments, toHex = true)
  }

  def store(writer: Writer, comments: String): Unit =
    storeImpl(writer, comments, toHex = false)

  private def storeImpl(
      writer: Writer,
      comments: String,
      toHex: Boolean
  ): Unit = {
    if (comments != null) {
      writeComments(writer, comments, toHex)
    }

    writer.write('#')
    writer.write(new Date().toString)
    writer.write(System.lineSeparator())

    entrySet().forEach { entry =>
      writer.write(
        encodeString(entry.getKey().asInstanceOf[String], isKey = true, toHex)
      )
      writer.write('=')
      writer.write(
        encodeString(
          entry.getValue().asInstanceOf[String],
          isKey = false,
          toHex
        )
      )
      writer.write(System.lineSeparator())
    }
    writer.flush()
  }

  @Deprecated
  def save(out: OutputStream, comments: String): Unit =
    store(out, comments)

  private def loadImpl(reader: Reader): Unit = synchronized {
    val br = new BufferedReader(reader)
    val valBuf = new jl.StringBuilder()
    var prevValueContinue = false
    var isKeyParsed = false
    var key: String = null
    var line: String = null

    while ({ line = br.readLine(); line != null }) {
      var i: Int = -1
      var ch: Char = Char.MinValue

      def getNextChar(): Char = {
        i += 1
        // avoid out of bounds if value is empty
        if (i < line.length())
          line.charAt(i)
        else
          ch
      }

      def parseUnicodeEscape(): Char = {
        val hexStr = line.substring(i, i + 4)
        // don't advance past the last char used
        i += 3
        Integer.parseInt(hexStr, 16).toChar
      }

      def isWhitespace(char: Char): Boolean =
        char == ' ' || char == '\t' || char == '\f'

      def isTokenKeySeparator(char: Char): Boolean =
        char == '=' || char == ':'

      def isKeySeparator(char: Char): Boolean =
        isTokenKeySeparator(char) || isWhitespace(char)

      def isEmpty(): Boolean =
        line.isEmpty()

      def isComment(): Boolean =
        line.startsWith("#") || line.startsWith("!")

      def oddBackslash(): Boolean = {
        var i = line.length()
        while (i > 0 && line.charAt(i - 1) == '\\')
          i -= 1
        (line.length() - i) % 2 != 0
      }

      def valueContinues(): Boolean = oddBackslash()

      def processChar(buf: jl.StringBuilder): Unit =
        if (ch == '\\') {
          ch = getNextChar()
          ch match {
            case 'u' =>
              getNextChar() // advance
              val uch = parseUnicodeEscape()
              buf.append(uch)
            case 't' => buf.append('\t')
            case 'f' => buf.append('\f')
            case 'r' => buf.append('\r')
            case 'n' => buf.append('\n')
            case _   => buf.append(ch)
          }
        } else {
          buf.append(ch)
        }

      def parseKey(): String = {
        val buf = new jl.StringBuilder()
        // ignore leading whitespace
        while (i < line.length && isWhitespace(ch)) {
          ch = getNextChar()
        }
        // key sep or empty value
        while (!isKeySeparator(ch) && i < line.length()) {
          processChar(buf)
          ch = getNextChar()
        }
        // ignore trailing whitespace
        while (i < line.length && isWhitespace(ch)) {
          ch = getNextChar()
        }
        // ignore non-space key separator
        if (i < line.length && isTokenKeySeparator(ch)) {
          ch = getNextChar()
        }
        isKeyParsed = true
        buf.toString()
      }

      def parseValue(): String = {
        // ignore leading whitespace
        while (i < line.length && isWhitespace(ch)) {
          ch = getNextChar()
        }

        // nothing but line continuation
        if (valueContinues() && i == line.length() - 1) {
          // ignore the final backslash
          ch = getNextChar()
        }

        while (i < line.length) {
          if (valueContinues() && i == line.length() - 1) {
            // ignore the final backslash
            ch = getNextChar()
          } else {
            processChar(valBuf)
            ch = getNextChar()
          }
        }
        valBuf.toString()
      }

      // run the parsing
      if (!(isComment() || isEmpty())) {
        ch = getNextChar()
        if (!isKeyParsed) {
          valBuf.setLength(0)
          key = parseKey()
          val value = parseValue()
          prevValueContinue = valueContinues()
          if (!prevValueContinue) {
            setProperty(key, value)
            isKeyParsed = false
          }
        } else if (prevValueContinue && valueContinues()) {
          val value = parseValue()
          prevValueContinue = valueContinues()
          setProperty(key, value)
        } else {
          val value = parseValue()
          setProperty(key, value)
          isKeyParsed = false
          prevValueContinue = false
        }
      }
    }
  }

  private def writeComments(
      writer: Writer,
      comments: String,
      toHex: Boolean
  ): Unit = {
    writer.write('#')
    val chars = comments.toCharArray
    var index = 0
    while (index < chars.length) {
      val ch = chars(index)
      if (ch <= 0xff) {
        if (ch == '\r' || ch == '\n') {
          def isCrlf =
            ch == '\r' && index + 1 < chars.length && chars(index + 1) == '\n'

          if (isCrlf) {
            index += 1
          }
          writer.write(System.lineSeparator())

          def noExplicitComment = {
            index + 1 < chars.length &&
            (chars(index + 1) != '#' && chars(index + 1) != '!')
          }

          if (noExplicitComment) {
            writer.write('#')
          }
        } else {
          writer.write(ch)
        }
      } else {
        if (toHex) {
          writer.write(unicodeToHexaDecimal(ch))
        } else {
          writer.write(ch)
        }
      }
      index += 1
    }
    writer.write(System.lineSeparator())
  }

  private def encodeString(
      string: String,
      isKey: Boolean,
      toHex: Boolean
  ): String = {
    val buffer = new jl.StringBuilder(200)
    var index = 0
    val length = string.length
    // leading element (value) spaces are escaped
    if (!isKey) {
      while (index < length && string.charAt(index) == ' ') {
        buffer.append("\\ ")
        index += 1
      }
    }

    while (index < length) {
      val ch = string.charAt(index)
      def fallback = {
        if (toHex && (ch < 0x20 || ch > 0x7e)) {
          buffer.append(unicodeToHexaDecimal(ch))
        } else {
          buffer.append(ch)
        }
      }
      (ch: @switch) match {
        case '\t' =>
          buffer.append("\\t")
        case '\n' =>
          buffer.append("\\n")
        case '\f' =>
          buffer.append("\\f")
        case '\r' =>
          buffer.append("\\r")
        case '\\' | '#' | '!' | '=' | ':' =>
          buffer.append('\\')
          buffer.append(ch)
        case ' ' =>
          if (isKey) buffer.append("\\ ")
          else fallback
        case _ =>
          fallback
      }
      index += 1
    }
    buffer.toString()
  }

  private def unicodeToHexaDecimal(ch: Int): Array[Char] = {
    def hexChar(x: Int): Char =
      if (x > 9) (x - 10 + 'A').toChar
      else (x + '0').toChar

    Array(
      '\\',
      'u',
      hexChar((ch >>> 12) & 15),
      hexChar((ch >>> 8) & 15),
      hexChar((ch >>> 4) & 15),
      hexChar(ch & 15)
    )
  }

  // TODO:
  // def loadFromXML(in: InputStream): Unit
  // def storeToXML(os: OutputStream, comment: String): Unit
  // def storeToXML(os: OutputStream, comment: String, encoding: String): Unit

  // Hashtable overrides required for JDK9+ compatibility, delegates to inner ConcurrentHashMap
  override def size(): Int = map.size()
  override def isEmpty(): Boolean = map.isEmpty()
  override def keys(): Enumeration[AnyRef] =
    Collections.enumeration(map.keySet())
  override def elements(): Enumeration[AnyRef] =
    Collections.enumeration(map.values())
  override def contains(value: Any): Boolean = map.contains(value)
  override def containsValue(value: Any): Boolean = map.containsValue(value)
  override def containsKey(key: Any): Boolean = map.containsKey(key)
  override def get(key: Any): AnyRef = map.get(key)
  override def put(key: AnyRef, value: AnyRef): AnyRef =
    map.put(key.asInstanceOf[AnyRef], value.asInstanceOf[AnyRef])
  override def remove(key: Any): AnyRef = map.remove(key)
  override def putAll(t: ju.Map[_ <: AnyRef, _ <: AnyRef]): Unit = map.putAll(t)
  override def clear(): Unit = map.clear()
  override def toString(): String = map.toString()
  override def keySet(): Set[AnyRef] =
    Collections.synchronizedSet(map.keySet())
  override def values(): Collection[AnyRef] =
    Collections.synchronizedCollection(map.values())
  override def entrySet(): Set[Map.Entry[AnyRef, AnyRef]] =
    Collections.synchronizedSet(map.entrySet())
  override def equals(o: Any): Boolean = map.equals(o)
  override def hashCode(): Int = map.hashCode()
  override def getOrDefault(key: Any, defaultValue: AnyRef): AnyRef =
    map.getOrDefault(key, defaultValue)
  override def forEach(action: BiConsumer[_ >: AnyRef, _ >: AnyRef]): Unit =
    map.forEach(action)
  override def replaceAll(
      function: BiFunction[_ >: AnyRef, _ >: AnyRef, _ <: AnyRef]
  ): Unit = map.replaceAll(function)
  override def putIfAbsent(key: AnyRef, value: AnyRef): AnyRef =
    map.putIfAbsent(key, value)
  override def remove(key: Any, value: Any): Boolean =
    map.remove(key, value)
  override def replace(
      key: AnyRef,
      oldValue: AnyRef,
      newValue: AnyRef
  ): Boolean =
    map.replace(key, oldValue, newValue)
  override def replace(key: AnyRef, value: AnyRef): AnyRef =
    map.replace(key, value)
  override def computeIfAbsent(
      key: AnyRef,
      mappingFunction: Function[_ >: AnyRef, _ <: AnyRef]
  ): AnyRef =
    map.computeIfAbsent(key, mappingFunction)
  override def computeIfPresent(
      key: AnyRef,
      remappingFunction: BiFunction[_ >: AnyRef, _ >: AnyRef, _ <: AnyRef]
  ): AnyRef =
    map.computeIfPresent(key, remappingFunction)
  override def compute(
      key: AnyRef,
      remappingFunction: BiFunction[_ >: AnyRef, _ >: AnyRef, _ <: AnyRef]
  ): AnyRef =
    map.compute(key, remappingFunction)
  override def merge(
      key: AnyRef,
      value: AnyRef,
      remappingFunction: BiFunction[_ >: AnyRef, _ >: AnyRef, _ <: AnyRef]
  ): AnyRef =
    map.merge(key, value, remappingFunction)

  /*override*/
  def rehash(): Unit = ()

  override def clone(): Properties = {
    val clone = super.clone().asInstanceOf[Properties]
    clone.map = new ju.concurrent.ConcurrentHashMap(map)
    clone
  }

}
