package scalatui.matching

class FuzzyMatcherSuite extends munit.FunSuite:
  test("exact matches rank before boundary and loose matches"):
    val ranked = FuzzyMatcher.filterStrings(
      "model",
      Vector("my-model", "migrations over data entry log", "model")
    )

    assertEquals(ranked.map(_.item), Vector("model", "my-model", "migrations over data entry log"))
    assert(ranked.head.score > ranked(1).score)

  test("boundary scoring improves ranking quality"):
    val ranked = FuzzyMatcher.filterStrings(
      "fb",
      Vector("fiber", "fooBar", "fast-boat", "fuzzy boilerplate")
    )

    assertEquals(ranked.map(_.item).take(3), Vector("fooBar", "fast-boat", "fuzzy boilerplate"))
    assert(ranked.map(_.item).indexOf("fiber") > ranked.map(_.item).indexOf("fuzzy boilerplate"))

  test("consecutive scoring ranks adjacent ordered characters before gapped characters"):
    val ranked = FuzzyMatcher.filterStrings("ab", Vector("xacbz", "xabz"))

    assertEquals(ranked.map(_.item), Vector("xabz", "xacbz"))
    assert(ranked.head.score > ranked(1).score)

  test("swapped alphanumeric query pairs match with a penalty"):
    val exact   = FuzzyMatcher.score("gpt4", "gpt4")
    val swapped = FuzzyMatcher.score("gp4t", "gpt4")

    assert(exact.nonEmpty)
    assert(swapped.nonEmpty)
    assert(exact.get.score > swapped.get.score)

  test("tokenized multi-word queries require every token"):
    val ranked = FuzzyMatcher.filterStrings(
      "model context",
      Vector("context window", "model runner", "model context protocol", "context model protocol")
    )

    assertEquals(ranked.map(_.item), Vector("model context protocol", "context model protocol"))
    assert(ranked.head.score > ranked(1).score)

  test("equal scores preserve stable input order"):
    val ranked = FuzzyMatcher.filter("same", Vector(1, 2, 3))(_ => "same")

    assertEquals(ranked.map(_.item), Vector(1, 2, 3))
    assertEquals(ranked.map(_.originalIndex), Vector(0, 1, 2))

  test("unicode lowercasing preserves original offsets"):
    val matchResult = FuzzyMatcher.score("item", "İtem")

    assertEquals(matchResult.map(_.positions), Some(Vector(0, 1, 2, 3)))

  test("case-insensitive matching supports supplementary-plane letters"):
    val matchResult = FuzzyMatcher.score("\uD801\uDC28", "\uD801\uDC00")

    assertEquals(matchResult.map(_.positions), Some(Vector(0)))

  test("no matches return None and empty filtered results"):
    assertEquals(FuzzyMatcher.score("xyz", "model"), None)
    assertEquals(FuzzyMatcher.filterStrings("xyz", Vector("model", "markdown")), Vector.empty)
