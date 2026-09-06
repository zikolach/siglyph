package scalatui.core

import scalatui.syntax.Equality.*
import scalatui.terminal.{
  TerminalImageProtocol,
  TerminalRenderControl,
  TerminalRenderControlDetails
}

import scala.collection.mutable

/**
 * Bounds for per-TUI fullscreen Kitty image retention on JVM and Scala Native.
 *
 * Retention stores only runtime image IDs, bounded payload identity data, geometry, generation, and
 * cleanup state. It does not retain payload strings, filenames, components, layout trees, or
 * application text. An entry is evicted after more than [[maxGenerationAge]] accepted fullscreen
 * generations offscreen. [[maxEntries]] uses oldest-visible-generation then image-ID order. The
 * runtime retains only active, reusable, and pending-cleanup IDs. A successful data-cleanup write
 * retires the pending ID, while a failed write leaves it available for stop cleanup.
 *
 * @param maxEntries
 *   Maximum retained Kitty image entries. Zero disables offscreen reuse.
 * @param maxGenerationAge
 *   Maximum accepted fullscreen generations an unchanged image may remain offscreen.
 */
final case class KittyImageRetentionOptions(
    maxEntries: Int = 64,
    maxGenerationAge: Int = 8
) derives CanEqual:
  require(maxEntries >= 0, "Kitty image retention count must be non-negative")
  require(maxGenerationAge >= 0, "Kitty image retention age must be non-negative")

private[core] final case class KittyRetentionFrame(
    frame: PreparedFrame,
    cleanup: Vector[TerminalRenderControl]
)

private[core] final class FullscreenKittyRetention(options: KittyImageRetentionOptions):
  private final case class SourceIdentity(length: Int, firstHash: Long, secondHash: Long)

  private final case class Entry(
      sourceIdentity: SourceIdentity,
      width: Int,
      height: Int,
      lastVisibleGeneration: Long,
      placementVisible: Boolean
  )

  private val entries           = mutable.HashMap.empty[Int, Entry]
  private val pendingCleanupIds = mutable.HashSet.empty[Int]
  private var activeIds         = Set.empty[Int]
  private var generation        = 0L

  def update(
      frame: PreparedFrame,
      previous: Option[PreparedFrame],
      repaintAll: Boolean
  ): KittyRetentionFrame =
    generation += 1L
    val frameActiveIds = frame.controls.flatMap(kittyImageId).toSet
    activeIds = frameActiveIds
    val upperCleanup   = mutable.HashSet.empty[Int]
    val lowerCleanup   = mutable.HashSet.empty[Int]

    entries.toVector.foreach { case (imageId, entry) =>
      if !frameActiveIds(
          imageId
        ) && generation - entry.lastVisibleGeneration > options.maxGenerationAge
      then
        entries.remove(imageId)
        upperCleanup += imageId
    }

    val transformed = frame.controls.map { placement =>
      placement.control.details match
        case kitty: TerminalRenderControlDetails.KittyImage =>
          val imageId        = kitty.imageId
          val sourceIdentity = identify(kitty.payload.value)
          val matching       = entries.get(imageId).filter(entry =>
            entry.sourceIdentity === sourceIdentity && entry.width === kitty.widthCells &&
              entry.height === kitty.heightCells
          )
          val admitted       = matching.orElse {
            entries.remove(imageId).foreach(_ => upperCleanup += imageId)
            admit(imageId, frameActiveIds, upperCleanup).map(_ =>
              Entry(
                sourceIdentity,
                kitty.widthCells,
                kitty.heightCells,
                generation,
                placementVisible = true
              )
            )
          }
          admitted match
            case Some(entry) =>
              val previousPlacement = previous.toVector.flatMap(_.controls).find(value =>
                kittyImageId(value).contains(imageId)
              )
              val samePlacement     = previousPlacement.exists(value =>
                value.row === placement.row && value.column === placement.column &&
                  value.control.width === placement.control.width &&
                  value.control.rows === placement.control.rows
              )
              val reuse             = matching.nonEmpty && (repaintAll || !samePlacement ||
                previousPlacement.exists(
                  _.control.details.isInstanceOf[TerminalRenderControlDetails.KittyPlacement]
                ))
              if reuse && previousPlacement.nonEmpty && (!samePlacement || repaintAll) then
                lowerCleanup += imageId
              else if repaintAll && previousPlacement.nonEmpty && matching.isEmpty then
                upperCleanup += imageId
              entries(imageId) = entry.copy(
                lastVisibleGeneration = generation,
                placementVisible = true
              )
              if reuse then
                placement.copy(control =
                  TerminalImageProtocol.placeKittyImage(
                    imageId,
                    kitty.widthCells,
                    kitty.heightCells
                  )
                )
              else placement
            case None        => placement
        case _                                              => placement
    }

    previous.toVector.flatMap(_.controls).flatMap(kittyImageId).distinct.foreach { imageId =>
      if !frameActiveIds(imageId) then
        entries.get(imageId) match
          case Some(entry) =>
            if entry.placementVisible then lowerCleanup += imageId
            entries(imageId) = entry.copy(placementVisible = false)
          case None        => upperCleanup += imageId
    }

    pendingCleanupIds ++= upperCleanup
    val cleanup = upperCleanup.toVector.sorted.map(dataCleanup) ++
      lowerCleanup.toVector.filterNot(upperCleanup).sorted.map(
        TerminalImageProtocol.deleteKittyPlacements
      )
    KittyRetentionFrame(frame.copy(controls = transformed), cleanup)

  def acknowledgeCleanup(controls: Vector[TerminalRenderControl]): Unit =
    controls.flatMap(kittyControlId).foreach(pendingCleanupIds.remove)

  def stopCleanup(): Vector[TerminalRenderControl] =
    (activeIds ++ entries.keySet ++ pendingCleanupIds).toVector.sorted.map(dataCleanup)

  /**
   * Invalidate placement-only reuse before attempting stop cleanup while retaining retry debt.
   *
   * A terminal write may execute a delete before reporting failure, so no retained upload may be
   * reused after an attempted stop-cleanup write.
   */
  def recordStopCleanupAttempt(): Unit =
    pendingCleanupIds ++= activeIds
    pendingCleanupIds ++= entries.keySet
    entries.clear()
    activeIds = Set.empty

  def acknowledgeStopCleanup(): Unit =
    entries.clear()
    pendingCleanupIds.clear()
    activeIds = Set.empty

  private[core] def ownershipCount: Int =
    (activeIds ++ entries.keySet ++ pendingCleanupIds).size

  private def admit(
      imageId: Int,
      activeIds: Set[Int],
      upperCleanup: mutable.HashSet[Int]
  ): Option[Unit] =
    if options.maxEntries === 0 then None
    else
      if entries.size >= options.maxEntries then
        entries.iterator
          .filterNot { case (candidateId, _) => activeIds(candidateId) || candidateId === imageId }
          .minByOption { case (candidateId, entry) =>
            entry.lastVisibleGeneration -> candidateId
          }
          .foreach { case (candidateId, _) =>
            entries.remove(candidateId)
            upperCleanup += candidateId
          }
      Option.when(entries.size < options.maxEntries)(())

  private def kittyImageId(placement: TerminalControlPlacement): Option[Int] =
    kittyControlId(placement.control)

  private def kittyControlId(control: TerminalRenderControl): Option[Int] =
    control.details match
      case kitty: TerminalRenderControlDetails.KittyImage           => Some(kitty.imageId)
      case kitty: TerminalRenderControlDetails.KittyPlacement       => Some(kitty.imageId)
      case TerminalRenderControlDetails.KittyCleanup(Some(imageId)) => Some(imageId)
      case _                                                        => None

  private def identify(value: String): SourceIdentity =
    var firstHash  = 1469598103934665603L
    var secondHash = 7809847782465536322L
    var index      = 0
    while index < value.length do
      val code = value.charAt(index).toLong
      firstHash = (firstHash ^ code) * 1099511628211L
      secondHash = (secondHash ^ (code + index.toLong)) * -7046029254386353131L
      index += 1
    SourceIdentity(value.length, firstHash, secondHash)

  private def dataCleanup(imageId: Int): TerminalRenderControl =
    TerminalRenderControl.cleanupForReplacement(
      TerminalImageProtocol.placeKittyImage(imageId, widthCells = 1, heightCells = 1)
    ).get
