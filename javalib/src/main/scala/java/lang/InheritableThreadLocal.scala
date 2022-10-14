package java.lang

class InheritableThreadLocal[T] extends ThreadLocal[T] {
  protected def childValue(parentValue: T): T = parentValue
  private[lang] final def getChildValue(parentValue: T): T = childValue(
    parentValue
  )
}
