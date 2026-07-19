package scalatui.components

import scalatui.core.{Component, OverlayHandle, OverlayHost, OverlayOptions, TUIContext}

import java.util.concurrent.CountDownLatch
import java.util.concurrent.atomic.AtomicInteger

class LoaderConcurrencySuite extends munit.FunSuite:
  test("concurrent loader cancellation has one callback and render winner"):
    val callerCount = 16
    val ready       = CountDownLatch(callerCount)
    val release     = CountDownLatch(1)
    val results     = Array.fill(callerCount)(false)
    val callbacks   = AtomicInteger(0)
    val context     = CountingContext()
    val loader      = CancellableLoader()
    loader.tuiContext_=(Some(context))
    loader.onCancel = () => callbacks.incrementAndGet()
    val callers     = Vector.tabulate(callerCount) { index =>
      Thread(() => {
        ready.countDown()
        release.await()
        results(index) = loader.cancel()
      })
    }

    callers.foreach(_.start())
    ready.await()
    release.countDown()
    callers.foreach(_.join(2000))

    assertEquals(callers.exists(_.isAlive), false)
    assertEquals(results.count(identity), 1)
    assertEquals(callbacks.get(), 1)
    assertEquals(context.renderRequests.get(), 1)
    assertEquals(loader.token.isCancelled, true)

  private final class CountingContext extends TUIContext:
    val renderRequests = AtomicInteger(0)

    override def requestRender(force: Boolean): Unit         = renderRequests.incrementAndGet()
    override def flushRender(): Unit                         = ()
    override def requestExit(): Unit                         = ()
    override def setFocus(component: Component | Null): Unit = ()
    override val overlays: OverlayHost                       = new OverlayHost:
      override def showOverlay(
          component: Component,
          options: OverlayOptions
      ): OverlayHandle = throw UnsupportedOperationException()
      override def hideOverlay(): Unit = ()
      override def hasOverlay: Boolean = false
