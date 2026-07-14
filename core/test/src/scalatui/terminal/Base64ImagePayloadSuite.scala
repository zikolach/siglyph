package scalatui.terminal

import scalatui.syntax.Equality.*

class Base64ImagePayloadSuite extends munit.FunSuite:
  test("accepts padded, empty, and decoder-valid unpadded payloads unchanged"):
    val values = Vector("", "TWFu", "TQ==", "TQ", "TWE")
    values.foreach { value =>
      assertEquals(Base64ImagePayload.from(value).map(_.value), Right(value))
    }

  test("covers every unpadded modulo-four length class"):
    assertEquals(Base64ImagePayload.from("TWFu").map(_.value), Right("TWFu"))
    assertEquals(Base64ImagePayload.from("A"), Left(invalid))
    assertEquals(Base64ImagePayload.from("TQ").map(_.value), Right("TQ"))
    assertEquals(Base64ImagePayload.from("TWE").map(_.value), Right("TWE"))

  test("validates large padded and unpadded payloads unchanged"):
    val largePrefix = "TWFu".repeat(16384)
    val accepted    = Vector(largePrefix + "TQ", largePrefix + "TQ==")
    accepted.foreach { value =>
      assertEquals(Base64ImagePayload.from(value).map(_.value), Right(value))
    }

    val rejected = Vector(largePrefix + "A", largePrefix + "TQ=A")
    rejected.foreach { value =>
      assertEquals(Base64ImagePayload.from(value), Left(invalid))
    }

  test("rejects malformed padding"):
    Vector("=", "T=Fu", "TWFu=", "TQ=", "TQ===", "TWE==", "TWFu====").foreach { value =>
      assertEquals(Base64ImagePayload.from(value), Left(invalid), value)
    }

  test("rejects whitespace, controls, URL-safe characters, and framing delimiters"):
    Vector(
      "TW Fu",
      "TW\nFu",
      "TW\tFu",
      "TW\u0000Fu",
      "TW\u0007Fu",
      "TW\u001bFu",
      "TW-Fu",
      "TW_Fu",
      "AAAA\u001b\\",
      "AAAA\u0007"
    ).foreach { value =>
      assertEquals(Base64ImagePayload.from(value), Left(invalid), value)
    }

  test("rejects null with a typed failure"):
    assertEquals(Base64ImagePayload.from(null), Left(invalid))

  test("encodes bytes as standard padded base64"):
    assertEquals(Base64ImagePayload.encode(Array.emptyByteArray).value, "")
    assertEquals(Base64ImagePayload.encode("M".getBytes).value, "TQ==")
    assertEquals(Base64ImagePayload.encode(Array(0xfb.toByte, 0xff.toByte)).value, "+/8=")

  test("uses value equality, hash code, and text representation"):
    val first  = Base64ImagePayload.from("TQ").toOption.get
    val second = Base64ImagePayload.from("TQ").toOption.get
    val other  = Base64ImagePayload.from("TWE").toOption.get

    assert(first === second)
    assert(first !== other)
    assertEquals(first.hashCode, second.hashCode)
    assertEquals(first.toString, "TQ")

  private val invalid = Base64ImagePayloadError.InvalidStandardBase64
