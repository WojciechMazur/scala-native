package java.util.concurrent
package locks

import java.util.concurrent.atomic.AtomicReference

@SerialVersionUID(3737899427754241961L)
abstract class AbstractOwnableSynchronizer extends java.io.Serializable {

  private val exclusiveOwnerThread = new AtomicReference[Thread]

  protected final def setExclusiveOwnerThread(t: Thread): Unit =
    exclusiveOwnerThread.set(t)

  protected final def getExclusiveOwnerThread(): Thread =
    exclusiveOwnerThread.get()

}
