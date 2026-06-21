package scalatui.unicode

/** Internal text case helpers shared by JVM and Scala Native code paths. */
private[scalatui] object TextCase:
  def lowercase(value: String): String =
    val builder = java.lang.StringBuilder()
    var offset  = 0
    while offset < value.length do
      val codePoint = value.codePointAt(offset)
      builder.appendCodePoint(Character.toLowerCase(codePoint))
      offset += Character.charCount(codePoint)
    builder.toString
