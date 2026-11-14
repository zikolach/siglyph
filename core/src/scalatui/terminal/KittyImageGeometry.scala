package scalatui.terminal

/** Validated nonempty source-pixel rectangle; coordinates and extents fit positive Int bounds. */
final case class ImagePixelRect(x: Int, y: Int, width: Int, height: Int) derives CanEqual:
  require(x >= 0 && y >= 0, "Pixel coordinates must be non-negative")
  require(width > 0 && height > 0, "Pixel extents must be positive")
  require(
    x.toLong + width <= Int.MaxValue && y.toLong + height <= Int.MaxValue,
    "Pixel rectangle exceeds coordinate bounds"
  )

/**
 * Declared source pixels and original display geometry, shared on JVM and Native without decoding.
 * Vertical clips retain the original row scale so nested clipping never compounds pixel rounding.
 * This metadata grants no terminal escape-string authority.
 */
final case class KittyImageGeometry(
    sourceDimensions: ImageDimensions,
    fullWidthCells: Int,
    fullRows: Int,
    topRow: Int,
    visibleRows: Int
) derives CanEqual:
  require(
    sourceDimensions.widthPx > 0 && sourceDimensions.heightPx > 0,
    "Source dimensions must be positive"
  )
  require(fullWidthCells > 0 && fullRows > 0, "Full display dimensions must be positive")
  require(
    topRow >= 0 && visibleRows > 0 && topRow.toLong + visibleRows <= fullRows,
    "Visible rows must be nonempty and within original geometry"
  )

  /** Source crop computed with floor(top) and ceil(bottom), always from original geometry. */
  def sourceRect: ImagePixelRect =
    val height = sourceDimensions.heightPx.toLong
    val top    = topRow.toLong * height / fullRows
    val bottom = ((topRow.toLong + visibleRows) * height + fullRows - 1L) / fullRows
    ImagePixelRect(0, top.toInt, sourceDimensions.widthPx, (bottom - top).toInt)

  /** Whether this geometry reserves fewer than all original display rows. */
  def isCropped: Boolean = topRow > 0 || visibleRows < fullRows

  /** Compose a nonempty vertical clip relative to the currently visible rows. */
  def clip(clippedTop: Int, rows: Int): KittyImageGeometry =
    require(
      clippedTop >= 0 && rows > 0 && clippedTop.toLong + rows <= visibleRows,
      "Clip must be nonempty and within visible rows"
    )
    copy(topRow = topRow + clippedTop, visibleRows = rows)
