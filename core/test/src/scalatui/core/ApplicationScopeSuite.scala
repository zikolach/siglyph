package scalatui.core

import java.util.concurrent.{CountDownLatch, TimeUnit}
import java.util.concurrent.atomic.AtomicReference
import scalatui.components.Text
import scalatui.terminal.VirtualTerminal
import scalatui.testing.TUITestDriver

class ApplicationScopeSuite extends munit.FunSuite:
  private def fixture(test: (TUI, TUITestDriver) => Unit): Unit =
    TUITestDriver.withSession(40, 3)(terminal =>
      TUI.fullscreen(terminal, Text("ready", paddingX = 0, paddingY = 0))
    )(driver => test(driver.tui, driver))

  test("scopes require a running session and validate capacities"):
    val tui = TUI(VirtualTerminal())
    intercept[IllegalArgumentException](tui.openApplicationScope())
    fixture { (running, _) =>
      intercept[IllegalArgumentException](running.openApplicationScope(0))
      intercept[IllegalArgumentException](running.openApplicationScope(4097))
    }

  test("updates serialize reentrant submissions and publish a coalesced frame"):
    val text = Text("before", paddingX = 0, paddingY = 0)
    TUITestDriver.withSession(40, 3)(terminal => TUI.fullscreen(terminal, text)) { driver =>
      val scope    = driver.tui.openApplicationScope()
      var order    = Vector.empty[Int]
      var depth    = 0
      var maxDepth = 0
      assertEquals(
        scope.submit {
          depth += 1
          maxDepth = math.max(maxDepth, depth)
          order :+= 1
          assertEquals(
            scope.submit {
              depth += 1
              maxDepth = math.max(maxDepth, depth)
              order :+= 2
              text.text = "updated"
              depth -= 1
            },
            ApplicationSubmission.Accepted
          )
          order :+= 3
          depth -= 1
        },
        ApplicationSubmission.Accepted
      )
      assertEquals(order, Vector(1, 3, 2))
      assertEquals(maxDepth, 1)
      assertEquals(scope.pendingCount, 0)
      driver.assertContains("updated")
    }

  test("per-scope capacity excludes the executing callback and rejects without evaluation"):
    fixture { (tui, _) =>
      val scope  = tui.openApplicationScope(2)
      var values = Vector.empty[Int]
      scope.submit {
        assertEquals(scope.submit { values :+= 1 }, ApplicationSubmission.Accepted)
        assertEquals(scope.submit { values :+= 2 }, ApplicationSubmission.Accepted)
        assertEquals(scope.pendingCount, 2)
        assertEquals(scope.submit { values :+= 99 }, ApplicationSubmission.Full)
      }
      assertEquals(values, Vector(1, 2))
    }

  test("shared coordinator capacity includes cancelled tombstones until drained"):
    fixture { (tui, _) =>
      val first    = tui.openApplicationScope(4096)
      val second   = tui.openApplicationScope(1)
      var executed = 0
      first.submit {
        (0 until 4096).foreach { _ =>
          assertEquals(first.submit { executed += 1 }, ApplicationSubmission.Accepted)
        }
        assertEquals(second.submit { executed += 1 }, ApplicationSubmission.Full)
        first.close()
        assertEquals(first.pendingCount, 0)
        assertEquals(second.submit { executed += 1 }, ApplicationSubmission.Full)
      }
      assertEquals(executed, 0)
      assertEquals(second.submit { executed += 1 }, ApplicationSubmission.Accepted)
      assertEquals(executed, 1)
    }

  test("self-close clears queued callback references without waiting for itself"):
    fixture { (tui, _) =>
      val scope = tui.openApplicationScope()
      var ran   = false
      scope.submit {
        scope.submit { ran = true }
        val pending = scope.state.pending.head
        scope.close()
        assert(pending.action.isEmpty)
        assert(scope.isClosed)
        assert(!scope.isQuiescent)
        assertEquals(scope.submit { ran = true }, ApplicationSubmission.Closed)
      }
      assert(!ran)
      assert(scope.isQuiescent)
      scope.close()
    }

  test("TUI stop cancels queued work and restart requires a fresh scope"):
    fixture { (tui, _) =>
      val old     = tui.openApplicationScope()
      var stale   = false
      old.submit {
        old.submit { stale = true }
        tui.stop()
        assert(old.isClosed)
      }
      assert(!stale)
      assert(old.isQuiescent)
      intercept[IllegalArgumentException](tui.openApplicationScope())
      tui.start()
      val fresh   = tui.openApplicationScope()
      assertEquals(old.submit { stale = true }, ApplicationSubmission.Closed)
      var updated = false
      assertEquals(fresh.submit { updated = true }, ApplicationSubmission.Accepted)
      assert(updated)
      assert(!stale)
    }

  test("opening scopes is bounded and explicit close releases a registry slot"):
    fixture { (tui, _) =>
      val scopes      = Vector.fill(64)(tui.openApplicationScope())
      intercept[IllegalArgumentException](tui.openApplicationScope())
      scopes.head.close()
      val replacement = tui.openApplicationScope()
      assert(!replacement.isClosed)
      tui.stop()
      assert(scopes.forall(_.isQuiescent))
      assert(replacement.isQuiescent)
    }

  test("callback failures enter the existing runtime channel and cancel remaining updates"):
    fixture { (tui, driver) =>
      val scope   = tui.openApplicationScope()
      val failure = new IllegalStateException("update failed")
      var stale   = false
      assertEquals(
        scope.submit {
          scope.submit { stale = true }
          throw failure
        },
        ApplicationSubmission.Accepted
      )
      val caught  = intercept[IllegalStateException](driver.flush())
      assert(caught eq failure)
      assert(scope.isQuiescent)
      assert(!stale)
      assertEquals(scope.submit { stale = true }, ApplicationSubmission.Closed)
    }

  test("callbacks do not hold the lifecycle lock"):
    fixture { (tui, driver) =>
      val scope = tui.openApplicationScope()
      scope.submit {
        val read   = new CountDownLatch(1)
        val worker = new Thread(() =>
          scope.pendingCount
          read.countDown()
        )
        worker.setDaemon(true)
        worker.start()
        assert(read.await(5L, TimeUnit.SECONDS))
        worker.join(5000L)
        assert(!worker.isAlive)
      }
      driver.flush()
    }

  test("external close waits for a claimed update and discards queued updates"):
    fixture { (tui, _) =>
      val scope    = tui.openApplicationScope()
      val began    = new CountDownLatch(1)
      val release  = new CountDownLatch(1)
      val error    = new AtomicReference[Throwable]()
      val events   = new java.util.concurrent.ConcurrentLinkedQueue[String]()
      val producer = new Thread(() =>
        try
          scope.submit {
            began.countDown()
            if !release.await(5L, TimeUnit.SECONDS) then
              throw new IllegalStateException("callback release timed out")
            events.add("callback finished")
          }
        catch case failure: Throwable => error.set(failure)
        ()
      )
      val closer   = new Thread(() =>
        try
          scope.close()
          events.add("close returned")
        catch case failure: Throwable => error.set(failure)
        ()
      )
      producer.setDaemon(true)
      closer.setDaemon(true)
      producer.start()
      try
        assert(began.await(5L, TimeUnit.SECONDS))
        assertEquals(scope.submit { events.add("stale") }, ApplicationSubmission.Accepted)
        closer.start()
        val cutoff = scope.state.cancelled.await(5L, TimeUnit.SECONDS)
        assert(cutoff)
        assert(scope.isClosed)
        assert(!scope.isQuiescent)
        assertEquals(scope.pendingCount, 0)
        assertEquals(scope.submit { events.add("late") }, ApplicationSubmission.Closed)
      finally
        release.countDown()
        producer.join(5000L)
        closer.join(5000L)
      assert(!producer.isAlive)
      assert(!closer.isAlive)
      assertEquals(Option(error.get()), None)
      assertEquals(events.poll(), "callback finished")
      assertEquals(events.poll(), "close returned")
      assert(events.isEmpty)
      assert(scope.isQuiescent)
    }
