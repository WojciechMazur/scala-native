package scala.scalanative.runtime

import NativeThread.{Registry, State}
import Thread.MainThread

object JoinNonDeamonThreads {
  def registerExitHook(): Unit = Shutdown.addHook { () =>
    def hasActiveNonDeadomThreads = Registry.aliveThreads.exists {
      nativeThread =>
        val thread = nativeThread.thread
        !thread.isDaemon() && thread.isAlive()
    }

    Registry.onMainThreadTermination(MainThread.getNativeThread)
    Registry.synchronized {
      while (hasActiveNonDeadomThreads) Registry.wait()
    }
  }
}
