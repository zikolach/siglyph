package scalatui.editing

import scalatui.unicode.Unicode

class WordNavigationSuite extends munit.FunSuite:
  test("moves by unicode-aware word and punctuation boundaries"):
    val clusters = Unicode.graphemeClusters("hello, 世界  again")
    assertEquals(WordNavigation.findWordBackward(clusters, clusters.length, _ => false), 11)
    assertEquals(WordNavigation.findWordBackward(clusters, 7, _ => false), 5)
    assertEquals(WordNavigation.findWordForward(clusters, 0, _ => false), 5)
    assertEquals(WordNavigation.findWordForward(clusters, 5, _ => false), 6)
    assertEquals(WordNavigation.findWordForward(clusters, 6, _ => false), 9)

  test("treats configured atomic segments as one cursor unit"):
    val clusters = Vector("a", "[paste #1]", "b")
    val isAtomic = (segment: String) => segment.startsWith("[paste")
    assertEquals(WordNavigation.findWordBackward(clusters, 2, isAtomic), 1)
    assertEquals(WordNavigation.findWordForward(clusters, 1, isAtomic), 2)
