package scalatui.matching

import scala.collection.mutable.ArrayBuffer

import scalatui.syntax.Equality.*

/**
 * Result of scoring one candidate against a fuzzy query.
 *
 * Scores are comparable only within this matcher: larger values are better. `positions` contains
 * the matched candidate character offsets used to produce the score and is intended for callers
 * that want to highlight matches. Matching is case-insensitive, dependency-free, and requires each
 * non-whitespace query token to match the candidate independently.
 */
final case class FuzzyMatch(score: Int, positions: Vector[Int]) derives CanEqual

/**
 * Candidate item decorated with its fuzzy score and matched positions.
 *
 * `originalIndex` is exposed so callers can keep deterministic tie-breaking behavior when they need
 * to compose fuzzy-ranked results with other ranking signals.
 */
final case class FuzzyRanked[A](
    item: A,
    text: String,
    score: Int,
    positions: Vector[Int],
    originalIndex: Int
) derives CanEqual

/**
 * Dependency-free fuzzy scoring and filtering helper for autocomplete and selector-style UIs.
 *
 * Matching supports ordered-character matches, word-boundary bonuses, consecutive bonuses, exact
 * match bonuses, adjacent swapped letter/digit query pairs, and tokenized multi-word queries. Equal
 * scores are ordered by original candidate position to keep filtering stable and deterministic.
 */
object FuzzyMatcher:
  private val ExactBonus       = 1000
  private val PrefixBonus      = 80
  private val WordPrefixBonus  = 50
  private val CharacterScore   = 16
  private val BoundaryBonus    = 30
  private val ConsecutiveBonus = 24
  private val FirstCharBonus   = 8
  private val GapPenalty       = 2
  private val LeadingPenalty   = 1
  private val SpanPenalty      = 3
  private val LengthPenalty    = 0
  private val SwapPenalty      = 15
  private val NoScore          = Int.MinValue / 4

  /**
   * Score a candidate for a query.
   *
   * Returns `None` when any non-whitespace query token cannot be matched. Empty or whitespace-only
   * queries match every candidate with score `0`.
   */
  def score(query: String, candidate: String): Option[FuzzyMatch] =
    scorePrepared(prepare(query), candidate)

  /**
   * Fuzzy-filter and rank candidates by text.
   *
   * Candidates that do not match every query token are omitted. Results are ordered by descending
   * score and then ascending original input index, preserving stable ordering for equal scores.
   */
  def filter[A](query: String, candidates: Iterable[A])(text: A => String): Vector[FuzzyRanked[A]] =
    val prepared = prepare(query)
    candidates.zipWithIndex.flatMap { case (candidate, index) =>
      val candidateText = text(candidate)
      scorePrepared(prepared, candidateText).map { matched =>
        FuzzyRanked(candidate, candidateText, matched.score, matched.positions, index)
      }
    }.toVector.sortBy(ranked => (-ranked.score, ranked.originalIndex))

  /** Convenience overload for ranking plain strings. */
  def filterStrings(query: String, candidates: Iterable[String]): Vector[FuzzyRanked[String]] =
    filter(query, candidates)(identity)

  private def prepare(query: String): PreparedQuery =
    PreparedQuery(
      query.split("\\s+").toVector.map(_.trim).filter(_.nonEmpty).map(PreparedToken.apply)
    )

  private def scorePrepared(query: PreparedQuery, candidate: String): Option[FuzzyMatch] =
    if query.tokens.isEmpty then Some(FuzzyMatch(0, Vector.empty))
    else
      val normalized = NormalizedText(candidate)
      val matches    = query.tokens.map(token => scoreToken(token, normalized))
      if matches.exists(_.isEmpty) then None
      else
        val resolved  = matches.flatten
        val positions = resolved.flatMap(_.positions).distinct.sorted.toVector
        val total     = resolved.map(_.score).sum - (query.tokens.length - 1)
        Some(FuzzyMatch(total, positions))

  private def scoreToken(token: PreparedToken, candidate: NormalizedText): Option[FuzzyMatch] =
    if token.isEmpty then Some(FuzzyMatch(0, Vector.empty))
    else if candidate.length < token.length - 1 then None
    else
      val queryLength     = token.length
      val candidateLength = candidate.length
      val tokenBonus      = precomputeTokenBonus(token, candidate)
      val scores          = Array.fill(queryLength + 1, candidateLength)(NoScore)
      val backPositions   = Array.fill(queryLength + 1, candidateLength)(-1)
      val consumedCounts  = Array.fill(queryLength + 1, candidateLength)(0)

      var queryIndex = 0
      while queryIndex < queryLength do
        val row           = scores(queryIndex)
        var bestGapScore  = NoScore
        var bestGapSource = -1
        var position      = 0
        while position < candidateLength do
          if queryIndex > 0 then
            val source = position - 1
            if source >= 0 && row(source) > NoScore then
              val gapScore = row(source) + GapPenalty * source
              if gapScore > bestGapScore then
                bestGapScore = gapScore
                bestGapSource = source

          if candidate.codePointAt(position) === token.codePointAt(queryIndex) then
            val (score, source) = bestTransitionScore(
              candidate,
              position,
              queryIndex,
              row,
              bestGapSource
            )
            updateScore(
              scores,
              backPositions,
              consumedCounts,
              queryIndex + 1,
              position,
              score,
              source,
              1
            )

          if queryIndex + 1 < queryLength &&
            position + 1 < candidateLength &&
            isLetterDigitPair(token.codePointAt(queryIndex), token.codePointAt(queryIndex + 1)) &&
            candidate.codePointAt(position) === token.codePointAt(queryIndex + 1) &&
            candidate.codePointAt(position + 1) === token.codePointAt(queryIndex)
          then
            val (firstScore, source) = bestTransitionScore(
              candidate,
              position,
              queryIndex,
              row,
              bestGapSource
            )
            if firstScore > NoScore then
              val swappedScore = scoreCharacter(
                candidate,
                position + 1,
                position,
                firstScore,
                isFirstQueryCharacter = false
              ) - SwapPenalty
              updateScore(
                scores,
                backPositions,
                consumedCounts,
                queryIndex + 2,
                position + 1,
                swappedScore,
                source,
                2
              )
          position += 1
        queryIndex += 1

      bestFinishedToken(
        token,
        candidate,
        scores(queryLength),
        backPositions,
        consumedCounts,
        tokenBonus
      )

  private def bestTransitionScore(
      candidate: NormalizedText,
      position: Int,
      queryIndex: Int,
      row: Array[Int],
      bestGapSource: Int
  ): (Int, Int) =
    if queryIndex === 0 then
      (scoreCharacter(candidate, position, -1, 0, isFirstQueryCharacter = true), -1)
    else
      var bestScore         = NoScore
      var bestSource        = -1
      if bestGapSource >= 0 then
        bestScore = scoreCharacter(
          candidate,
          position,
          bestGapSource,
          row(bestGapSource),
          isFirstQueryCharacter = false
        )
        bestSource = bestGapSource
      val consecutiveSource = position - 1
      if consecutiveSource >= 0 && row(consecutiveSource) > NoScore then
        val consecutiveScore = scoreCharacter(
          candidate,
          position,
          consecutiveSource,
          row(consecutiveSource),
          isFirstQueryCharacter = false
        )
        if consecutiveScore > bestScore then
          bestScore = consecutiveScore
          bestSource = consecutiveSource
      (bestScore, bestSource)

  private def updateScore(
      scores: Array[Array[Int]],
      backPositions: Array[Array[Int]],
      consumedCounts: Array[Array[Int]],
      queryRow: Int,
      position: Int,
      score: Int,
      source: Int,
      consumed: Int
  ): Unit =
    if score > scores(queryRow)(position) then
      scores(queryRow)(position) = score
      backPositions(queryRow)(position) = source
      consumedCounts(queryRow)(position) = consumed

  private def bestFinishedToken(
      token: PreparedToken,
      candidate: NormalizedText,
      row: Array[Int],
      backPositions: Array[Array[Int]],
      consumedCounts: Array[Array[Int]],
      tokenBonus: Int
  ): Option[FuzzyMatch] =
    var best = Option.empty[FuzzyMatch]
    var end  = 0
    while end < row.length do
      if row(end) > NoScore then
        val positions = reconstructPositions(token.length, end, backPositions, consumedCounts)
        val matched   =
          finalizeTokenScore(token, candidate, FuzzyMatch(row(end), positions), tokenBonus)
        best match
          case Some(current) if current.score >= matched.score => ()
          case _                                               => best = Some(matched)
      end += 1
    best

  private def reconstructPositions(
      queryLength: Int,
      endPosition: Int,
      backPositions: Array[Array[Int]],
      consumedCounts: Array[Array[Int]]
  ): Vector[Int] =
    val positions = ArrayBuffer.empty[Int]
    var row       = queryLength
    var end       = endPosition
    while row > 0 && end >= 0 do
      val consumed = consumedCounts(row)(end)
      if consumed === 2 then
        positions += end - 1
        positions += end
      else positions += end
      end = backPositions(row)(end)
      row -= consumed
    positions.distinct.sorted.toVector

  private def scoreCharacter(
      candidate: NormalizedText,
      position: Int,
      previousPosition: Int,
      previousScore: Int,
      isFirstQueryCharacter: Boolean
  ): Int =
    val gap = if previousPosition < 0 then position else position - previousPosition - 1
    previousScore + CharacterScore +
      (if isBoundary(candidate, position) then BoundaryBonus else 0) +
      (if previousPosition >= 0 && position === previousPosition + 1 then ConsecutiveBonus else 0) +
      (if isFirstQueryCharacter && position === 0 then FirstCharBonus else 0) -
      (if previousPosition < 0 then position * LeadingPenalty else gap * GapPenalty)

  private def finalizeTokenScore(
      token: PreparedToken,
      candidate: NormalizedText,
      matched: FuzzyMatch,
      tokenBonus: Int
  ): FuzzyMatch =
    val positions  = matched.positions
    val span       = positions.last - positions.head + 1
    val spanCost   = (span - token.length) * SpanPenalty
    val finalScore = matched.score + tokenBonus - spanCost
    FuzzyMatch(finalScore, positions.map(candidate.originalOffset))

  private def precomputeTokenBonus(token: PreparedToken, candidate: NormalizedText): Int =
    val exactBonus  = if candidate.normalized === token.normalized then ExactBonus else 0
    val prefixBonus = if startsWith(candidate, token, 0) then PrefixBonus else 0
    val wordBonus   = if containsWordPrefix(candidate, token) then WordPrefixBonus else 0
    val lengthCost  = math.max(0, candidate.length - token.length) * LengthPenalty
    exactBonus + prefixBonus + wordBonus - lengthCost

  private def containsWordPrefix(candidate: NormalizedText, token: PreparedToken): Boolean =
    var index = 0
    var found = false
    while !found && index < candidate.length do
      if isBoundary(candidate, index) && startsWith(candidate, token, index) then
        found = true
      index += 1
    found

  private def startsWith(candidate: NormalizedText, token: PreparedToken, start: Int): Boolean =
    if start + token.length > candidate.length then false
    else
      var index = 0
      var found = true
      while found && index < token.length do
        if candidate.codePointAt(start + index) !== token.codePointAt(index) then found = false
        index += 1
      found

  private def isBoundary(text: NormalizedText, position: Int): Boolean =
    if position === 0 then true
    else
      val previous = text.originalCodePointAt(position - 1)
      val current  = text.originalCodePointAt(position)
      !Character.isLetterOrDigit(previous) ||
      (Character.isLowerCase(previous) && Character.isUpperCase(current)) ||
      (Character.isDigit(previous) && Character.isLetter(current)) ||
      (Character.isLetter(previous) && Character.isDigit(current))

  private def isLetterDigitPair(left: Int, right: Int): Boolean =
    (Character.isLetter(left) && Character.isDigit(right)) ||
      (Character.isDigit(left) && Character.isLetter(right))

  private def normalizedString(codePoints: Array[Int]): String =
    val builder = java.lang.StringBuilder()
    codePoints.foreach(builder.appendCodePoint)
    builder.toString

  private final case class PreparedQuery(tokens: Vector[PreparedToken])

  private final class PreparedToken private (val codePoints: Array[Int]):
    val normalized: String           = normalizedString(codePoints)
    def isEmpty: Boolean             = codePoints.isEmpty
    def length: Int                  = codePoints.length
    def codePointAt(index: Int): Int = codePoints(index)

  private object PreparedToken:
    def apply(text: String): PreparedToken =
      val codePoints = ArrayBuffer.empty[Int]
      var offset     = 0
      while offset < text.length do
        val codePoint = text.codePointAt(offset)
        codePoints += Character.toLowerCase(codePoint)
        offset += Character.charCount(codePoint)
      new PreparedToken(codePoints.toArray)

  private final class NormalizedText private (
      val original: String,
      val codePoints: Array[Int],
      val offsets: Array[Int]
  ):
    val normalized: String                   = normalizedString(codePoints)
    def length: Int                          = codePoints.length
    def codePointAt(index: Int): Int         = codePoints(index)
    def originalOffset(index: Int): Int      = offsets(index)
    def originalCodePointAt(index: Int): Int = original.codePointAt(offsets(index))

  private object NormalizedText:
    def apply(text: String): NormalizedText =
      val codePoints = ArrayBuffer.empty[Int]
      val offsets    = ArrayBuffer.empty[Int]
      var offset     = 0
      while offset < text.length do
        val codePoint = text.codePointAt(offset)
        codePoints += Character.toLowerCase(codePoint)
        offsets += offset
        offset += Character.charCount(codePoint)
      new NormalizedText(text, codePoints.toArray, offsets.toArray)
