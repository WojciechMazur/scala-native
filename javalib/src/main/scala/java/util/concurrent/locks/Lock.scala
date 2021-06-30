/*
 * Written by Doug Lea with assistance from members of JCP JSR-166
 * Expert Group and released to the public domain, as explained at
 * http://creativecommons.org/publicdomain/zero/1.0/
 */

package java.util.concurrent
package locks

trait Lock {

  def lock(): Unit

  def lockInterruptibly(): Unit

  def newCondition(): Condition

  def tryLock(): Boolean

  def tryLock(time: Long, unit: TimeUnit): Boolean

  def unlock(): Unit

}
