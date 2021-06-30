/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */
package java.util
package concurrent

trait BlockingQueue[E] extends java.util.Queue[E] {

  def add(e: E): Boolean

  override def offer(e: E): Boolean

  def put(e: E): Unit

  def offer(e: E, timeout: Long, unit: TimeUnit): Boolean

  def take(): E

  def poll(timeout: Long, unit: TimeUnit): E

  def remainingCapacity(): Int

  def remove(o: Any): Boolean

  def contains(o: Any): Boolean

  def drainTo(c: java.util.Collection[_ >: E]): Int

  def drainTo(c: java.util.Collection[_ >: E], maxElements: Int): Int

}
