/*
 * Scala (https://www.scala-lang.org)
 *
 * Copyright EPFL and Lightbend, Inc.
 *
 * Licensed under Apache License 2.0
 * (http://www.apache.org/licenses/LICENSE-2.0).
 *
 * See the NOTICE file distributed with this work for
 * additional information regarding copyright ownership.
 */

package scala.concurrent
import java.util.{concurrent => juc}
import java.util.Collection

// Scala 2.12 backport of forkjoin package, since originall sources were written in Java.
// Does not make a good sense to reimplement again already deprecated API. 
package object forkjoin {
  type ForkJoinPool = juc.ForkJoinPool
  object ForkJoinPool {
    type ForkJoinWorkerThreadFactory = juc.ForkJoinPool.ForkJoinWorkerThreadFactory
    type ManagedBlocker              = juc.ForkJoinPool.ManagedBlocker

    val defaultForkJoinWorkerThreadFactory: ForkJoinWorkerThreadFactory = juc.ForkJoinPool.defaultForkJoinWorkerThreadFactory
    def managedBlock(blocker: ManagedBlocker): Unit                     = juc.ForkJoinPool.managedBlock(blocker)
  }

  type ForkJoinTask[T] = juc.ForkJoinTask[T]
  object ForkJoinTask extends scala.Serializable {
    def adapt(runnable: Runnable): ForkJoinTask[_]                           = juc.ForkJoinTask.adapt(runnable)
    def adapt[T](callable: juc.Callable[_ <: T]): ForkJoinTask[T]            = juc.ForkJoinTask.adapt(callable)
    def adapt[T](runnable: Runnable, result: T): ForkJoinTask[T]             = juc.ForkJoinTask.adapt(runnable, result)
    def getPool(): ForkJoinPool                                              = juc.ForkJoinTask.getPool()
    def getQueuedTaskCount(): Int                                            = juc.ForkJoinTask.getQueuedTaskCount()
    def getSurplusQueuedTaskCount(): Int                                     = juc.ForkJoinTask.getSurplusQueuedTaskCount()
    def helpQuiesce(): Unit                                                  = juc.ForkJoinTask.helpQuiesce()
    def inForkJoinPool(): Boolean                                            = juc.ForkJoinTask.inForkJoinPool()
    def invokeAll[T <: ForkJoinTask[_]](tasks: Collection[T]): Collection[T] = juc.ForkJoinTask.invokeAll(tasks)
    def invokeAll[T](t1: ForkJoinTask[T]): Unit                              = juc.ForkJoinTask.invokeAll(t1)
    def invokeAll[T](tasks: ForkJoinTask[T]*): Unit                          = juc.ForkJoinTask.invokeAll(tasks: _*)
  }

  type ForkJoinWorkerThread   = juc.ForkJoinWorkerThread
  type LinkedTransferQueue[T] = juc.LinkedTransferQueue[T]
  type RecursiveAction        = juc.RecursiveAction
  type RecursiveTask[T]       = juc.RecursiveTask[T]

  type ThreadLocalRandom      = juc.ThreadLocalRandom
  object ThreadLocalRandom extends scala.Serializable {
    // For source compatibility, current must declare the empty argument list.
    // Having no argument list makes more sense since it doesn't have any side effects,
    // but existing callers will break if they invoked it as `current()`.
    def current() = juc.ThreadLocalRandom.current()
  }
}
